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
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.roi.geom.real.Polygon2D;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

public class MetaSegRandomForestClassifier {

	private Instances trainingData;
	private FastRandomForest forest;
	
	OpService ops = new Context( OpService.class, OpMatchingService.class ).getService( OpService.class );
	private boolean is2D;

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
//		int relative_weight_bad = 1;

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
				double prob = forest.distributionForInstance( ins )[ 1 ];
				costs.put( segment, -prob );
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
			Polygon2D poly = ops.geom().contour( ( RandomAccessibleInterval ) hypothesis.getRegion(), true );
			area = ops.geom().size( poly ).get();
			perimeter = ops.geom().boundarySize( poly ).get();
			convexity = ops.geom().convexity( poly ).get();
			circularity = ops.geom().circularity( poly ).get();
			solidity = ops.geom().solidity( poly ).get();
		} else {
			Mesh mesh = ops.geom().marchingCubes( ( ( RandomAccessibleInterval ) hypothesis.getRegion() ) );
			area = ops.geom().size( mesh ).get();
			perimeter = ops.geom().boundarySize( mesh ).get();
			convexity = ops.geom().convexity( mesh ).get();
			circularity = ops.geom().sphericity( mesh ).get();
			solidity = ops.geom().solidity( mesh ).get();
		}
		DenseInstance ins = new DenseInstance( weight, new double[] { area, perimeter, convexity, circularity, solidity, category } );
		return ins;
	}

	public void setIs2D( boolean b ) {
		is2D = b;
	}

}
