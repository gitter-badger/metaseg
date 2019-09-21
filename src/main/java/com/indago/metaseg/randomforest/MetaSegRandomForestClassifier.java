package com.indago.metaseg.randomforest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.scijava.Context;

import com.indago.data.segmentation.LabelingSegment;

import hr.irb.fastRandomForest.FastRandomForest;
import net.imagej.mesh.Mesh;
import net.imagej.ops.OpMatchingService;
import net.imagej.ops.OpService;
import net.imagej.ops.geom.geom2d.DefaultCircularity;
import net.imagej.ops.geom.geom2d.DefaultContour;
import net.imagej.ops.geom.geom2d.DefaultConvexityPolygon;
import net.imagej.ops.geom.geom2d.DefaultPerimeterLength;
import net.imagej.ops.geom.geom2d.DefaultSizePolygon;
import net.imagej.ops.geom.geom2d.DefaultSolidityPolygon;
import net.imagej.ops.geom.geom3d.DefaultConvexityMesh;
import net.imagej.ops.geom.geom3d.DefaultSolidityMesh;
import net.imagej.ops.geom.geom3d.DefaultSphericity;
import net.imagej.ops.geom.geom3d.DefaultSurfaceArea;
import net.imagej.ops.geom.geom3d.DefaultVolumeMesh;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.roi.geom.real.Polygon2D;
import net.imglib2.type.logic.NativeBoolType;
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

	public MetaSegRandomForestClassifier( boolean is2D ) {
		this.is2D = is2D;
		prematchOps();
	}

	private void prematchOps() {
		Img< NativeBoolType > image2d = ArrayImgs.booleans( new boolean[] { true }, 1, 1 );
		buildPolygoneOp = ops.op( DefaultContour.class, image2d, true );
		Polygon2D poly = buildPolygoneOp.calculate( image2d );
		Mesh mesh = ops.geom().marchingCubes( ArrayImgs.booleans( new boolean[] { true }, 1, 1, 1 ) );
		polygonSolidityOp = ops.op( DefaultSolidityPolygon.class, poly );
		meshSolidityOp = ops.op( DefaultSolidityMesh.class, mesh );
		polygonConvexityOp = ops.op( DefaultConvexityPolygon.class, poly );
		meshConvexityOp = ops.op( DefaultConvexityMesh.class, mesh );
		polygonAreaOp = ops.op( DefaultSizePolygon.class, poly );
		meshAreaOp = ops.op( DefaultVolumeMesh.class, mesh );
		polygonPerimeterOp = ops.op( DefaultPerimeterLength.class, poly );
		meshPerimeterOp = ops.op( DefaultSurfaceArea.class, mesh );
		polygonCircularityOp = ops.op( DefaultCircularity.class, poly );
		meshCircularityOp = ops.op( DefaultSphericity.class, mesh );
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
		attInfo.add( new Attribute( "class", Arrays.asList( "bad", "good" ) ) );
		Instances table = new Instances( "foo bar", attInfo, 1 );
		table.setClassIndex( table.numAttributes() - 1 );
		return table;
	}


	public void initializeTrainingData( List< LabelingSegment > goodHypotheses, List< LabelingSegment > badHypotheses ) {
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

	public Map< LabelingSegment, Double > predict( List< LabelingSegment > predictionSet ) {
		Map< LabelingSegment, Double > costs = new HashMap<>();
		Instances testData = newTable();
		for ( final LabelingSegment segment : predictionSet ) {
			DenseInstance ins = extractFeaturesFromHypotheses( segment, 1, 0 );
			ins.setDataset( testData );
			try {
				double prob = forest.distributionForInstance( ins )[ 1 ]; // probability of class 1 ("good" class)
				if ( prob < 0.5 ) {
					costs.put( segment, prob );
				} else {
					costs.put( segment, -prob );
				}

			} catch ( Exception e ) {
				e.printStackTrace();
			}
		}

		return costs;
	}

	private DenseInstance extractFeaturesFromHypotheses( LabelingSegment hypothesis, double weight, int category ) {
		double area;
		double perimeter;
		double convexity;
		double circularity;
		double solidity;
		if ( is2D ) {
			Polygon2D poly = buildPolygoneOp.calculate( hypothesis.getRegion() );
			area = polygonAreaOp.calculate( poly ).get();
			perimeter = polygonPerimeterOp.calculate( poly ).get();
			convexity = polygonConvexityOp.calculate( poly ).get();
			circularity = polygonCircularityOp.calculate( poly ).get();
			solidity = polygonSolidityOp.calculate( poly ).get();
		} else {
			Mesh mesh = ops.geom().marchingCubes( ( ( RandomAccessibleInterval ) hypothesis.getRegion() ) );
			area = meshAreaOp.calculate( mesh ).get();
			perimeter = meshPerimeterOp.calculate( mesh ).get();
			convexity = meshConvexityOp.calculate( mesh ).get();
			circularity = meshCircularityOp.calculate( mesh ).get();
			solidity = meshSolidityOp.calculate( mesh ).get();
		}
		DenseInstance ins = new DenseInstance( weight, new double[] { area, perimeter, convexity, circularity, solidity, category } );
		return ins;
	}
}
