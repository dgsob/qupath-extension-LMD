## LMD Support for QuPath
This extension aims to provide an open-source solution for automating 
the segmentation process within Leica's laser microdissection software. It exports 
detections from [QuPath](https://qupath.github.io/) to a file format compatible with LMD7.

### Installation
Download a JAR file from releases and drag it into QuPath's main 
window as described [here](https://qupath.readthedocs.io/en/0.4/docs/intro/extensions.html#installing-extensions).

### Workflow overview
1. Acquire an image of your slide (e.g. with a slide scanner).
2. Segment cells in QuPath. 
3. Expand segmentations if needed.
4. Add calibration points to your image.
5. Export the objects, optionally choose a collector 
and assign object classifications to its caps.
6. Import the XML into Leica's software.

### Examples

- Link 1
- Link 2

### Usage Details
 - **Calibration Points**: In order to add calibration points, create 3 separate 
annotations with a single point each and name the first one "calibration1" 
and the rest accordingly. Note that although the points' locations technically 
can be random, you will need to find the exact same 3 points in Leica's LMD 
software viewer. The default 
recommendation is to put the first point around top left, second - top right 
and third - bottom right corner. These are corners of the slide as seen 
in the microscope's viewer, not QuPath (a standard scan would have these at 
bottom right, top right and top left corner in QuPath).


- **Objects Expanding**: You will most likely need to expand your segmentations 
having the laser's aperture in mind. To do so, select objects to expand 
(Ctrl+Alt+D for all detections) and head to 'Utilities/Expand Selected Detections'.
Set the radius and the desired behaviour when two objects of different classes 
intersect - you can either exclude these objects or set an order of priority for 
objects of diffferent classes. An object with higher priority will be preserved.
Objects of the same class will be merged if intersecting. Note that this won't 
process annotations and the new, enlarged objects will be generic detections 
regardless of their previous type. The processing may take some time in case 
of many objects (>5000) and may result in data loss. The smaller the number of
processed objects, the better. 


- **Exporting**: Each object which is not an 'annotation' will be counted
as shape to be cut out by the LMD and exported to the output XML, the calibration 
points are always included in the export. You can assign objects of different 
classifications to a specific collector's cap at this point. Choose a desired 
collector and upon confirmation, you'll be prompted to assign the classes.


- **Mirroring**: This aims to help in case the view of your slide in QuPath and LMD
software do not match (e.g. due to flipping slide to cut ROIs). It creates a copy
of the image and its content mirrored either horizontally or vertically. 
It is also possible to create a copy without mirroring, which differs from
QuPath's image duplication in a way, that it will also copy all objects present in
the image.


- **Converting**: Since only detections processing is supported, the utility for 
converting in-between annotations and detections (which enclose an area) is provided. 
This preserves only the features, which matter in the context of LMD, i.e. object's 
ROI, its class and potential name, and won't account e.g. for parent/child relations 
(similarly to expanding).



