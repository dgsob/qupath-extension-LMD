package org.cecad.lmd.common;

import com.fasterxml.jackson.databind.JsonNode;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import qupath.lib.geom.Point2;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.PathObjectPainter;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionModel;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.gui.prefs.PathPrefs;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import static org.cecad.lmd.common.Constants.ObjectTypes.ANNOTATION;

public class ObjectUtils {
    public static PathObject mergeObjects(final Collection<PathObject> objects, final PathClass objectClass) {
        ROI shapeNew = null;
        for (PathObject object : objects) {
            if (shapeNew == null)
                shapeNew = object.getROI();
            else if (shapeNew.getImagePlane().equals(object.getROI().getImagePlane()))
                shapeNew = RoiTools.combineROIs(shapeNew, object.getROI(), RoiTools.CombineOp.ADD);
            else {
                Dialogs.showErrorNotification("Error", "It seems as if the processed objects were from different image planes. " +
                        "Please reload the image and try again.");
            }
        }
        assert shapeNew != null;
        if (objectClass != null)
            return PathObjects.createDetectionObject(shapeNew, objectClass);
        else
            return PathObjects.createDetectionObject(shapeNew);
    }

    public static Polygon convertRoiToGeometry(PathObject object){
        List<Point2> points = object.getROI().getAllPoints();

        Coordinate[] coords = new Coordinate[points.size() + 1]; // +1 to close the polygon
        for (int i = 0; i < points.size(); i++) {
            Point2 point = points.get(i);
            coords[i] = new Coordinate(point.getX(), point.getY());
        }
        coords[points.size()] = coords[0]; // Close the polygon

        GeometryFactory geomFactory = new GeometryFactory();
        LinearRing linearRing = geomFactory.createLinearRing(coords);
        return geomFactory.createPolygon(linearRing, null);
    }

    public static List<PathObject> sortObjectsByPriority(final Collection<PathObject> objects, List<String> priorityRanking) {
        List<PathObject> sortedObjects = new ArrayList<>(objects);

        sortedObjects.sort((obj1, obj2) -> {
            PathClass class1 = obj1.getPathClass();
            PathClass class2 = obj2.getPathClass();

            String class1Name = class1 != null ? class1.getName() : null;
            String class2Name = class2 != null ? class2.getName() : null;

            int index1 = priorityRanking.indexOf(class1Name);
            int index2 = priorityRanking.indexOf(class2Name);

            if (index1 != -1 && index2 == -1) {
                return -1; // obj1 comes before obj2
            } else if (index1 == -1 && index2 != -1) {
                return 1; // obj2 comes before obj1
            }

            return Integer.compare(index1, index2);
        });

        return sortedObjects;
    }

    public static PathObject mirrorObject(PathObject object, int scaleX, int scaleY, int translateX, int translateY){
        ROI roi = object.getROI();
        roi = roi.scale(scaleX, scaleY);
        roi = roi.translate(-translateX, -translateY);
        PathClass objectClass = object.getPathClass();
        String objectName = object.getName();
        PathObject newObject = null;

        if (object.isDetection()) {
            if (objectClass != null)
                newObject = PathObjects.createDetectionObject(roi, objectClass);
            else
                newObject = PathObjects.createDetectionObject(roi);

            if (objectName != null)
                newObject.setName(objectName);
        }
        else if (object.isAnnotation()) {
            if (objectClass != null)
                newObject = PathObjects.createAnnotationObject(roi, objectClass);
            else
                newObject = PathObjects.createAnnotationObject(roi);

            if (objectName != null)
                newObject.setName(objectName);
        }
        return newObject;
    }

    public static void addObjectAccountingForParent(PathObjectHierarchy hierarchy, PathObject object, PathObject parent) {
        if (parent != null)
            parent.addChildObject(object);
        else
            hierarchy.addObject(object);
    }

    public static Collection<PathObject> filterOutAnnotations(Collection<PathObject> objects){
        return objects.stream().filter(PathObject::isDetection).toList();
    }

    public static Map<String, Integer> countObjectsByUniqueClass(JsonNode features){
        Map<String, Integer> featureCounts = new HashMap<>();
        for (JsonNode feature : features) {
            String objectType = feature.path("properties").path("objectType").asText();
            JsonNode classificationNode = feature.path("properties").path("classification");
            String featureClassName = classificationNode.path("name").asText();
            if (!ANNOTATION.equals(objectType))
                featureCounts.put(featureClassName, featureCounts.getOrDefault(featureClassName, 0) + 1);
        }
        return featureCounts;
    }

    public static Collection<PathObject> getCalibrationPoints(Collection<PathObject> objects, String... names) {
        return objects.stream()
                .filter(p -> p.isAnnotation() && p.getROI().isPoint() && containsName(p.getDisplayedName(), names))
                .collect(Collectors.toList());
    }
    static boolean containsName(String targetName, String... names) {
        for (String name : names) {
            if (targetName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static void repaintDetectionsWithCustomStroke(Collection<PathObject> objects,
                                                         double customStrokeValueMicrons,
                                                         ImageServer<BufferedImage> server,
                                                         OverlayOptions overlayOptions,
                                                         PathObjectSelectionModel selectionModel,
                                                         double downsample) {
        PixelCalibration calibration = server.getPixelCalibration();
        double customStrokeValuePixels = micronsToPixels(customStrokeValueMicrons, calibration);
        PathPrefs.detectionStrokeThicknessProperty().setValue(customStrokeValuePixels);

        BufferedImage compatibleImage = new BufferedImage(server.getWidth(), server.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = compatibleImage.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        PathObjectPainter.paintSpecifiedObjects(graphics, objects, overlayOptions, selectionModel, downsample);

        graphics.dispose();
    }

    public static double micronsToPixels(double inputMicrons, PixelCalibration calibration){
        double outputPixels = 0;
        if (calibration.hasPixelSizeMicrons())
            outputPixels = inputMicrons / calibration.getAveragedPixelSizeMicrons();
        else
            outputPixels = inputMicrons;

        return outputPixels;
    }
}
