package com.indago.metaseg.randomforest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.scijava.Context;

import com.indago.data.segmentation.LabelingSegment;
import com.indago.metaseg.ui.model.MetaSegModel;

import hr.irb.fastRandomForest.FastRandomForest;
import net.imagej.ImgPlus;
import net.imagej.mesh.Mesh;
import net.imagej.ops.OpMatchingService;
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
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

public class MetaSegRandomForestClassifier {

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
	private final MetaSegModel model;
	private ImgPlus< DoubleType > img;

	public MetaSegRandomForestClassifier( boolean is2D, MetaSegModel model ) {
		this.is2D = is2D;
		this.model = model;
		img = model.getRawData();
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

	private Instances trainingData;
	private FastRandomForest forest;
	
	OpService ops = new Context( OpService.class, OpMatchingService.class ).getService( OpService.class );
	private final boolean is2D;

	private static Instances newTable() {
		ArrayList< Attribute > attInfo = new ArrayList<>();
		attInfo.add( new Attribute( "area" ) );
		attInfo.add( new Attribute( "perimeter" ) );
		attInfo.add( new Attribute( "convexity" ) );
		attInfo.add( new Attribute( "circularity" ) );
		attInfo.add( new Attribute( "solidity" ) );
		attInfo.add( new Attribute( "boundarysizeconvexhull" ) );
		attInfo.add( new Attribute( "elongation" ) );
		attInfo.add( new Attribute( "boundarypixelsum" ) );
		attInfo.add( new Attribute( "facepixelsum" ) );
		attInfo.add( new Attribute( "class", Arrays.asList( "bad", "good" ) ) );
		Instances table = new Instances( "foo bar", attInfo, 1 );
		table.setClassIndex( table.numAttributes() - 1 );
		return table;
	}


	public void initializeTrainingData(
			List< ValuePair< LabelingSegment, Integer > > goodHypotheses,
			List< ValuePair< LabelingSegment, Integer > > badHypotheses ) {
		trainingData = newTable();
		double relative_weight_bad = ( double ) goodHypotheses.size() / badHypotheses.size(); //Use when not using crossvalidation(CV), CV does stratified sampling already
//		double relative_weight_bad = 1;

		for ( int i = 0; i < goodHypotheses.size(); i++ ) {
			DenseInstance ins = extractFeaturesFromHypotheses( goodHypotheses.get( i ), 1, 1 );
			trainingData.add( ins );
		}
		for ( int i = 0; i < badHypotheses.size(); i++ ) {
			DenseInstance ins = extractFeaturesFromHypotheses( badHypotheses.get( i ), relative_weight_bad, 0 );
			trainingData.add( ins );
		}
	}

	public void buildRandomForest() {
		forest = new FastRandomForest();
		forest.setNumTrees( 100 );
	}


	public void train() throws Exception {
		forest.buildClassifier( trainingData );
	}

	public Map< LabelingSegment, Double > predict( List< ValuePair< LabelingSegment, Integer > > predictionSet ) {
		Map< LabelingSegment, Double > costs = new HashMap<>();
		Instances testData = newTable();
		for ( final ValuePair< LabelingSegment, Integer > segment : predictionSet ) {
			DenseInstance ins = extractFeaturesFromHypotheses( segment, 1, 0 );
			ins.setDataset( testData );
			try {
				double prob = forest.distributionForInstance( ins )[ 1 ]; // probability of class 1 ("good" class)
				if ( prob < 0.5 ) {
					costs.put( segment.getA(), prob );
				} else {
					costs.put( segment.getA(), -prob );
				}

			} catch ( Exception e ) {
				e.printStackTrace();
			}
		}

		return costs;
	}

	private DenseInstance extractFeaturesFromHypotheses( ValuePair< LabelingSegment, Integer > valuePair, double weight, int category ) {
		double area;
		double perimeter;
		double convexity;
		double circularity;
		double solidity;
		double boundarysizeconvexhull;
		double elongation;
		double boundaryPixelSum;
		double pixelSumNormalized;
		LabelingSegment hypothesis = valuePair.getA();
		Integer time = valuePair.getB();
		if ( is2D ) {
			Polygon2D poly = buildPolygoneOp.calculate( hypothesis.getRegion() );
			area = polygonAreaOp.calculate( poly ).get();
			perimeter = polygonPerimeterOp.calculate( poly ).get();
			convexity = polygonConvexityOp.calculate( poly ).get();
			circularity = polygonCircularityOp.calculate( poly ).get();
			solidity = polygonSolidityOp.calculate( poly ).get();
			boundarysizeconvexhull = polygonBoundarySizeConvexHullOp.calculate( poly ).get();
			elongation = polygonElongationOp.calculate( poly ).get();
			boundaryPixelSum = computeBoundaryPixelSum( hypothesis, time );
			pixelSumNormalized = computeFacePixelSum( hypothesis, time ) / area;
		} else {
			Mesh mesh = ops.geom().marchingCubes( ( ( RandomAccessibleInterval ) hypothesis.getRegion() ) );
			area = meshAreaOp.calculate( mesh ).get();
			perimeter = meshPerimeterOp.calculate( mesh ).get();
			convexity = meshConvexityOp.calculate( mesh ).get();
			circularity = meshCircularityOp.calculate( mesh ).get();
			solidity = meshSolidityOp.calculate( mesh ).get();
			boundarysizeconvexhull = meshBoundarySizeConvexHullOp.calculate( mesh ).get();
			elongation = meshElongationOp.calculate( mesh ).get();
			boundaryPixelSum = computeBoundaryPixelSum( hypothesis, time );
			pixelSumNormalized = computeFacePixelSum( hypothesis, time ) / area;
		}
		DenseInstance ins = new DenseInstance( weight, new double[] { area,
																	  perimeter,
																	  convexity,
																	  circularity,
																	  solidity,
																	  boundarysizeconvexhull,
																	  elongation,
																	  boundaryPixelSum,
																	  pixelSumNormalized,
																	  category } );
		return ins;
	}


	private double computeFacePixelSum( LabelingSegment hypothesis, Integer time ) {
		double sum = 0d;
		IntervalView< DoubleType > retSlice = Views.hyperSlice(
				img,
				model.getTimeDimensionIndex(),
				time );
		Cursor< DoubleType > cursor = Regions.sample( hypothesis.getRegion(), retSlice ).cursor();
		while ( cursor.hasNext() ) {
			sum = sum + cursor.next().get();
		}
		return sum;
	}

	private double computeBoundaryPixelSum( LabelingSegment hypothesis, Integer time ) {
		Boundary maskBoundary = new Boundary<>( hypothesis.getRegion() );
		IntervalView< DoubleType > retSlice = Views.hyperSlice(
				img,
				model.getTimeDimensionIndex(),
				time );
		double sum = 0d;
		Cursor< DoubleType > cursor = Regions.sample( maskBoundary, retSlice ).cursor();
		while ( cursor.hasNext() ) {
			sum = sum + cursor.next().get();
		}

		return sum;
	}
}
