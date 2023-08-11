package org.dgsob;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;

import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ParameterPanelFX;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.RoiTools;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

//import static qupath.lib.analysis.DistanceTools.computeDistance;

public class ExpandObjectsCommand {
    private ExpandObjectsCommand(){

    }

    public static void runObjectsExpansion(ImageData<BufferedImage> imageData){

        ImageServer<BufferedImage> server = imageData.getServer();

        PathObjectHierarchy hierarchy = imageData.getHierarchy();

        Collection<PathObject> pathObjects = getSelected(hierarchy);

        assert pathObjects != null;

        Collection<PathObject> newObjects = new ArrayList<>();

        int objectsNumber = pathObjects.size();
        if(objectsNumber>500)
            Dialogs.showInfoNotification("LMD Notification", "You have chosen " + objectsNumber + " objects to expand. This may take a while.");

        ParameterList params = new ParameterList()
                .addDoubleParameter("radiusMicrons", "Expansion radius", 3, GeneralTools.micrometerSymbol(),
                        "Distance to expand ROI")
                .addChoiceParameter("priorityClass",
                        "Set object of which class to keep if two diffrent overlap or exlude both",
                        "Positive", Arrays.asList("Exclude both", "Positive", "Negative"));

        boolean confirmed = Dialogs.showConfirmDialog("Expand selected", new ParameterPanelFX(params).getPane());

        if(confirmed) {
            double radiusPixels;
            PixelCalibration cal = server.getPixelCalibration();
            if (cal.hasPixelSizeMicrons())
                radiusPixels = params.getDoubleParameterValue("radiusMicrons") / cal.getAveragedPixelSizeMicrons();
            else
                radiusPixels = params.getDoubleParameterValue("radiusMicrons");

            for (PathObject pathObject : pathObjects) {

                ROI roi = pathObject.getROI();
                Geometry geometry = roi.getGeometry();
                Geometry geometry2 = BufferOp.bufferOp(geometry, radiusPixels, BufferParameters.DEFAULT_QUADRANT_SEGMENTS);
                ROI roi2 = GeometryTools.geometryToROI(geometry2, ImagePlane.getPlane(roi));
                PathObject detection2 = PathObjects.createDetectionObject(roi2, pathObject.getPathClass());
                detection2.setName(pathObject.getName());
                detection2.setColor(pathObject.getColor());
                newObjects.add(detection2);
            }
            hierarchy.removeObjects(pathObjects, false);
            hierarchy.getSelectionModel().clearSelection();

            // Process overlapping objects: merge, exclude both or exclude one of the two overlapping depending on their class
            Collection<PathObject> objectsToRemove = new ArrayList<>();
            Collection<PathObject> objectsToAdd = new ArrayList<>();
            while(!newObjects.isEmpty()) {
                newObjects = processOverlappingObjects(hierarchy, newObjects, objectsToAdd, objectsToRemove,
                        params.getChoiceParameterValue("priorityClass"));
            }
            hierarchy.removeObjects(objectsToRemove, false);
            hierarchy.addObjects(objectsToAdd);
        }

    }

    private static Collection<PathObject> processOverlappingObjects(final PathObjectHierarchy hierarchy,
                                                                           Collection<PathObject> newObjects,
                                                                           Collection<PathObject> objectsToAdd,
                                                                           Collection<PathObject> objectsToRemoveFromHierarchy,
                                                                           Object priorityClass){
        Collection<PathObject> remainingObjects = new ArrayList<>(newObjects);
        Collection<PathObject> objectsToMerge = new ArrayList<>();
        Collection<PathObject> objectsToRemoveFromProcessed = new ArrayList<>();
        Collection<PathObject> objectToAddBackToProcessed = new ArrayList<>();
        boolean isOverlapping = false;
        boolean isSameClass = true;

        for (PathObject object : newObjects){
            PathClass objectClass = object.getPathClass();
            Polygon polygon = convertRoiToGeometry(object);
            remainingObjects.remove(object);

            for (PathObject otherObject : remainingObjects){
                PathClass otherObjectClass = otherObject.getPathClass();
                Polygon otherPolygon = convertRoiToGeometry(otherObject);
                if (polygon.intersects(otherPolygon)){
                    isOverlapping = true;
                    if (objectClass == otherObjectClass){
                        objectsToMerge.add(object);
                        objectsToMerge.add(otherObject);
                        break;
                    }
                    else{
                        isSameClass = false;
                        if (priorityClass.equals("Exclude both")){
                            objectsToRemoveFromProcessed.add(object);
                            objectsToRemoveFromProcessed.add(otherObject);
                            break;
                        }
                        else{
                            // assumes max 2 class scenario
                            if (priorityClass.toString().equals(objectClass.toString())) {
                                Dialogs.showConfirmDialog("","I am priority class object, I'm removing the other intersecting me rn");
                                objectsToRemoveFromProcessed.add(otherObject);
                                objectToAddBackToProcessed.add(object);
                            } else {
                                Dialogs.showConfirmDialog("","I am not a priority object, I am getting removed for intersecting one rn");
                                Dialogs.showConfirmDialog("","Let's check if you don't lie, your class is " + objectClass + " and priority class is " + priorityClass);
                                // we should remove object from the remaining here but it was done earlier
                            }
                            break;
                        }
                    }
                }
            }
            if (isOverlapping) {
                if (isSameClass) {
                    remainingObjects.removeAll(objectsToMerge); //we already removed object earlier,
                    // but I don't see intuitive other option to remove otherObject as well other than this for now
                    remainingObjects.add(mergeObjects(objectsToMerge, objectClass));
                }
                else{
                    Dialogs.showConfirmDialog("","Size of the remaining: " + remainingObjects.size());
                    remainingObjects.removeAll(objectsToRemoveFromProcessed);
                    remainingObjects.addAll(objectToAddBackToProcessed);
                    Dialogs.showConfirmDialog("","Size of the remaining after removal of peasants: " + remainingObjects.size());
                }
            }
            else{
                Dialogs.showConfirmDialog("","This should fire only once at the end");
                objectsToAdd.add(object);
            }
            break;
        }
        return remainingObjects;
    }

            // ---------------------------------------------------------------------------------------------------------
            // Handle objects that are not selected to be expanded but intersect our object in the result of its expansion.
            // These are already in hierarchy, newObjects are not.
            // It is for sure an improvement from iterating over all objects but the problem is it relies on objects centroids, not intersection checking.
            // We could try to somehow enlarge the ROI and iterate over these objects identically to newObjects above.
//            Collection<PathObject> alreadyInHierarchy = hierarchy.getObjectsForROI(null, object.getROI());
//            if (!alreadyInHierarchy.isEmpty()){
//                for (PathObject otherObject : alreadyInHierarchy){
//                    if (objectClass == null && otherObject.getPathClass() != null ||
//                            objectClass != null && otherObject.getPathClass() == null ||
//                            objectClass != null && !objectClass.equals(otherObject.getPathClass())){
//
//                        areAllTheSameClass = false;
//                    }
//                    // We could either add each object to objectsToMerge here or all at once below, doesn't matter I guess
//                }
//                objectsToMerge.addAll(alreadyInHierarchy);
//                objectsToRemove.addAll(alreadyInHierarchy);
//                isOverlapping = true;
//            }
            // ---------------------------------------------------------------------------------------------------------
    private static Polygon convertRoiToGeometry(PathObject object){
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
    private static PathObject mergeObjects(final Collection<PathObject> objects, final PathClass objectClass) {
        ROI shapeNew = null;
        for (PathObject object : objects) {
            if (shapeNew == null)
                shapeNew = object.getROI();
            else if (shapeNew.getImagePlane().equals(object.getROI().getImagePlane()))
                shapeNew = RoiTools.combineROIs(shapeNew, object.getROI(), RoiTools.CombineOp.ADD);
            else {
                Dialogs.showErrorMessage("Error", "It seems as if the processed objects were from different image planes. " +
                        "Please reload the image and try again.");
            }
        }
        assert shapeNew != null;
        if (objectClass != null)
            return PathObjects.createDetectionObject(shapeNew, objectClass);
        else
            return PathObjects.createDetectionObject(shapeNew);
    }

    private static Collection<PathObject> getSelected(PathObjectHierarchy hierarchy){
        if (hierarchy.getSelectionModel().noSelection()) {
            Dialogs.showErrorMessage("Error", "No selection. Please, select detections to expand.");
            return null;
        }
        return hierarchy.getSelectionModel().getSelectedObjects();
    }
}
