package com.indago.metaseg.threadedfeaturecomputation;

import org.scijava.plugin.Parameter;

import com.indago.data.segmentation.LabelingSegment;
import com.indago.metaseg.ui.model.MetaSegModel;
import com.indago.metaseg.ui.view.FeatureSelection;
import com.indago.metaseg.ui.view.FeatureType;

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
	private final FeatureSelection featureSelection;
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

	public MetaSegSingleSegmentFeatureComputation(final MetaSegModel model, final FeatureSelection featureSelection) {
		parentModel = model;
		this.is2D = model.is2D();
		this.featureSelection = featureSelection;
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
		LabelingSegment hypothesis = valuePair.getA();
		Integer time = valuePair.getB();
		double area = 0;
		double perimeter = 0;
		FeaturesRow featureRow =
				new FeaturesRow( time );
		if ( is2D ) {
			Polygon2D poly = buildPolygoneOp.calculate( hypothesis.getRegion() );
			if(featureSelection.isSelected( FeatureType.AREA ) || featureSelection.isSelected( FeatureType.NORMALIZED_FACE_PIXEL_SUM )) {
				area = polygonAreaOp.calculate( poly ).get();
				featureRow.setValue( FeatureType.AREA, area );
			}
			if ( featureSelection.isSelected( FeatureType.PERIMETER ) || featureSelection.isSelected( FeatureType.NORMALIZED_BOUNDARY_PIXEL_SUM ) ) {
				perimeter = polygonPerimeterOp.calculate( poly ).get();
				featureRow.setValue( FeatureType.PERIMETER, perimeter );
			}
			if ( featureSelection.isSelected( FeatureType.CONVEXITY ) ) {
				featureRow.setValue( FeatureType.CONVEXITY, polygonConvexityOp.calculate( poly ).get() );
			}
			if ( featureSelection.isSelected( FeatureType.CIRCULARITY ) ) {
				featureRow.setValue( FeatureType.CIRCULARITY, polygonCircularityOp.calculate( poly ).get() );
			}
			if ( featureSelection.isSelected( FeatureType.SOLIDITY ) ) {
				featureRow.setValue( FeatureType.SOLIDITY, polygonSolidityOp.calculate( poly ).get() );
			}
			if ( featureSelection.isSelected( FeatureType.BOUNDARY_SIZE_CONVEX_HULL ) ) {
				featureRow.setValue( FeatureType.BOUNDARY_SIZE_CONVEX_HULL, polygonBoundarySizeConvexHullOp.calculate( poly ).get() );
			}
			if ( featureSelection.isSelected( FeatureType.NORMALIZED_BOUNDARY_PIXEL_SUM ) ) {
				featureRow.setValue( FeatureType.NORMALIZED_BOUNDARY_PIXEL_SUM, computeBoundaryPixelSum( hypothesis, time ) / perimeter );
			}
			if ( featureSelection.isSelected( FeatureType.NORMALIZED_FACE_PIXEL_SUM ) ) {
				featureRow.setValue( FeatureType.NORMALIZED_FACE_PIXEL_SUM, computeFacePixelSum( hypothesis, time ) / area );
			}
			
//			elongation = polygonElongationOp.calculate( poly ).get();

		} else {
			Mesh mesh = ops.geom().marchingCubes( ( ( RandomAccessibleInterval ) hypothesis.getRegion() ) );

			if ( featureSelection.isSelected( FeatureType.AREA ) || featureSelection.isSelected( FeatureType.NORMALIZED_FACE_PIXEL_SUM ) ) {
				area = meshAreaOp.calculate( mesh ).get();
				featureRow.setValue( FeatureType.AREA, area );
			}
			if ( featureSelection.isSelected( FeatureType.PERIMETER ) || featureSelection.isSelected( FeatureType.NORMALIZED_BOUNDARY_PIXEL_SUM ) ) {
				perimeter = meshPerimeterOp.calculate( mesh ).get();
				featureRow.setValue( FeatureType.PERIMETER, perimeter );
			}
			if ( featureSelection.isSelected( FeatureType.CONVEXITY ) ) {
				featureRow.setValue( FeatureType.CONVEXITY, meshConvexityOp.calculate( mesh ).get() );
			}
			if ( featureSelection.isSelected( FeatureType.CIRCULARITY ) ) {
				featureRow.setValue( FeatureType.CIRCULARITY, meshCircularityOp.calculate( mesh ).get() );
			}
			if ( featureSelection.isSelected( FeatureType.SOLIDITY ) ) {
				featureRow.setValue( FeatureType.SOLIDITY, meshSolidityOp.calculate( mesh ).get() );
			}
			if ( featureSelection.isSelected( FeatureType.BOUNDARY_SIZE_CONVEX_HULL ) ) {
				featureRow.setValue( FeatureType.BOUNDARY_SIZE_CONVEX_HULL, meshBoundarySizeConvexHullOp.calculate( mesh ).get() );
			}
			if ( featureSelection.isSelected( FeatureType.NORMALIZED_BOUNDARY_PIXEL_SUM ) ) {
				featureRow.setValue( FeatureType.NORMALIZED_BOUNDARY_PIXEL_SUM, computeBoundaryPixelSum( hypothesis, time ) / perimeter );
			}
			if ( featureSelection.isSelected( FeatureType.NORMALIZED_FACE_PIXEL_SUM ) ) {
				featureRow.setValue( FeatureType.NORMALIZED_FACE_PIXEL_SUM, computeFacePixelSum( hypothesis, time ) / area );
			}

//			elongation = meshElongationOp.calculate( mesh ).get();

		}

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
