package com.indago.metaseg.threadedfeaturecomputation;

import org.scijava.plugin.Parameter;

import com.indago.data.segmentation.LabelingSegment;
import com.indago.metaseg.ui.model.MetaSegModel;

import net.imagej.ImgPlus;
import net.imagej.mesh.Mesh;
import net.imagej.ops.OpService;
import net.imagej.ops.geom.geom2d.DefaultBoundarySizeConvexHullPolygon;
import net.imagej.ops.geom.geom2d.DefaultCircularity;
import net.imagej.ops.geom.geom2d.DefaultContour;
import net.imagej.ops.geom.geom2d.DefaultConvexityPolygon;
import net.imagej.ops.geom.geom2d.DefaultElongation;
import net.imagej.ops.geom.geom2d.DefaultPerimeterLength;
import net.imagej.ops.geom.geom2d.DefaultSizePolygon;
import net.imagej.ops.geom.geom2d.DefaultSolidityPolygon;
import net.imagej.ops.geom.geom3d.DefaultConvexityMesh;
import net.imagej.ops.geom.geom3d.DefaultMainElongation;
import net.imagej.ops.geom.geom3d.DefaultSolidityMesh;
import net.imagej.ops.geom.geom3d.DefaultSphericity;
import net.imagej.ops.geom.geom3d.DefaultSurfaceArea;
import net.imagej.ops.geom.geom3d.DefaultSurfaceAreaConvexHullMesh;
import net.imagej.ops.geom.geom3d.DefaultVolumeMesh;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.roi.Regions;
import net.imglib2.roi.boundary.Boundary;
import net.imglib2.roi.geom.real.Polygon2D;
import net.imglib2.type.logic.NativeBoolType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class MetaSegSingleSegmentFeatureComputation {

	private final MetaSegModel parentModel;
	private DefaultSolidityPolygon polygonSolidityOp;
	private DefaultSolidityMesh meshSolidityOp;
	private DefaultConvexityPolygon polygonConvexityOp;
	private DefaultConvexityMesh meshConvexityOp;
	private DefaultSizePolygon polygonAreaOp;
	private DefaultVolumeMesh meshAreaOp;
	private DefaultPerimeterLength polygonPerimeterOp;
	private DefaultSurfaceArea meshPerimeterOp;
	private DefaultCircularity polygonCircularityOp;
	private DefaultSphericity meshCircularityOp;
	private DefaultContour buildPolygoneOp;
	private DefaultBoundarySizeConvexHullPolygon polygonBoundarySizeConvexHullOp;
	private DefaultSurfaceAreaConvexHullMesh meshBoundarySizeConvexHullOp;
	private DefaultElongation polygonElongationOp;
	private DefaultMainElongation meshElongationOp;
	private ImgPlus< DoubleType > img;

	@Parameter
	private OpService ops;

	private final boolean is2D;

	public MetaSegSingleSegmentFeatureComputation( final MetaSegModel model ) {
		parentModel = model;
		this.is2D = model.is2D();
		img = model.getRawData();
		model.getContext().inject( this );
		prematchOps();
	}

	private void prematchOps() {
		Img< NativeBoolType > image2d = ArrayImgs.booleans( new boolean[] { true }, 1, 1 );
		buildPolygoneOp = ops.op( DefaultContour.class, image2d, true );
		Polygon2D poly = buildPolygoneOp.calculate( image2d );
		Mesh mesh = ops.geom().marchingCubes( ArrayImgs.booleans( new boolean[] { true }, 1, 1, 1 ) );

		polygonAreaOp = ops.op( DefaultSizePolygon.class, poly ); //Area of polygon
		meshAreaOp = ops.op( DefaultVolumeMesh.class, mesh ); //Volume 
		polygonPerimeterOp = ops.op( DefaultPerimeterLength.class, poly );
		meshPerimeterOp = ops.op( DefaultSurfaceArea.class, mesh );
		polygonConvexityOp = ops.op( DefaultConvexityPolygon.class, poly ); //Ratio of perimeters of convex hull over original contour 
		meshConvexityOp = ops.op( DefaultConvexityMesh.class, mesh );
		polygonCircularityOp = ops.op( DefaultCircularity.class, poly ); // 4pi(area/perimeter^2)
		meshCircularityOp = ops.op( DefaultSphericity.class, mesh ); // https://en.wikipedia.org/wiki/Sphericity
		polygonSolidityOp = ops.op( DefaultSolidityPolygon.class, poly ); // Area/ConvexArea, signify an object having an irregular boundary, or containing holes
		meshSolidityOp = ops.op( DefaultSolidityMesh.class, mesh ); // Volume/convex volume
		polygonBoundarySizeConvexHullOp = ops.op( DefaultBoundarySizeConvexHullPolygon.class, poly ); //computes the perimeter of convex hull
		meshBoundarySizeConvexHullOp = ops.op( DefaultSurfaceAreaConvexHullMesh.class, mesh ); //computes the surface area of convex hull of mesh
		polygonElongationOp = ops.op( DefaultElongation.class, poly );
		meshElongationOp = ops.op( DefaultMainElongation.class, mesh );
	}

	public FeaturesRow extractFeaturesFromHypothesis( ValuePair< LabelingSegment, Integer > valuePair ) {
		double area;
		double perimeter;
		double convexity;
		double circularity;
		double solidity;
		double boundarySizeConvexHull;
		double elongation;
		double normalizedBoundaryPixelSum;
		double normalizedFacePixelSum;
		LabelingSegment hypothesis = valuePair.getA();
		Integer time = valuePair.getB();
		if ( is2D ) {
			Polygon2D poly = buildPolygoneOp.calculate( hypothesis.getRegion() );
			area = polygonAreaOp.calculate( poly ).get();
			perimeter = polygonPerimeterOp.calculate( poly ).get();
			convexity = polygonConvexityOp.calculate( poly ).get();
			circularity = polygonCircularityOp.calculate( poly ).get();
			solidity = polygonSolidityOp.calculate( poly ).get();
			boundarySizeConvexHull = polygonBoundarySizeConvexHullOp.calculate( poly ).get();
//			elongation = polygonElongationOp.calculate( poly ).get();
			normalizedBoundaryPixelSum = computeBoundaryPixelSum( hypothesis, time ) / perimeter;
			normalizedFacePixelSum = computeFacePixelSum( hypothesis, time ) / area;

		} else {
			Mesh mesh = ops.geom().marchingCubes( ( ( RandomAccessibleInterval ) hypothesis.getRegion() ) );
			area = meshAreaOp.calculate( mesh ).get();
			perimeter = meshPerimeterOp.calculate( mesh ).get();
			convexity = meshConvexityOp.calculate( mesh ).get();
			circularity = meshCircularityOp.calculate( mesh ).get();
			solidity = meshSolidityOp.calculate( mesh ).get();
			boundarySizeConvexHull = meshBoundarySizeConvexHullOp.calculate( mesh ).get();
			elongation = meshElongationOp.calculate( mesh ).get();
			normalizedBoundaryPixelSum = computeBoundaryPixelSum( hypothesis, time ) / perimeter;
			normalizedFacePixelSum = computeFacePixelSum( hypothesis, time ) / area;
		}
		FeaturesRow featureRow =
				new FeaturesRow( time, area, perimeter, convexity, circularity, solidity, boundarySizeConvexHull, normalizedBoundaryPixelSum, normalizedFacePixelSum );

		return featureRow;
	}

	private double computeFacePixelSum( LabelingSegment hypothesis, Integer time ) {
		double sum = 0d;
		IntervalView< DoubleType > retSlice = extractSliceAtTime( time );
		Cursor< DoubleType > cursor = Regions.sample( hypothesis.getRegion(), retSlice ).cursor();
		while ( cursor.hasNext() ) {
			sum = sum + cursor.next().get();
		}
		return sum;
	}

	private double computeBoundaryPixelSum( LabelingSegment hypothesis, Integer time ) {
		Boundary maskBoundary = new Boundary<>( hypothesis.getRegion() );
		IntervalView< DoubleType > retSlice = extractSliceAtTime( time );
		double sum = 0d;
		Cursor< DoubleType > cursor = Regions.sample( maskBoundary, retSlice ).cursor();
		while ( cursor.hasNext() ) {
			sum = sum + cursor.next().get();
		}

		return sum;
	}

	private IntervalView< DoubleType > extractSliceAtTime( Integer time ) {
		IntervalView< DoubleType > retSlice;
		if ( parentModel.getNumberOfFrames() > 1 ) {
			retSlice = Views
					.hyperSlice(
							img,
							parentModel
									.getTimeDimensionIndex(),
							time );
		} else {
			long[] mininterval = new long[ img.numDimensions() ];
			long[] maxinterval = new long[ img.numDimensions() ];
			for ( int i = 0; i < mininterval.length - 1; i++ ) {
				mininterval[ i ] = img.min( i );
				maxinterval[ i ] = img.max( i );
			}
			retSlice = Views.interval( img, mininterval, maxinterval );
		}
		return retSlice;
	}
}
