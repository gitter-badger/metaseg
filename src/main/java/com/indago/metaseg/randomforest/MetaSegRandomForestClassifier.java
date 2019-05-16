package com.indago.metaseg.randomforest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.scijava.Context;

import com.indago.data.segmentation.LabelingSegment;
import com.indago.metaseg.data.LabelingFrames;

import hr.irb.fastRandomForest.FastRandomForest;
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


	private static Instances newTable() {
		ArrayList< Attribute > attInfo = new ArrayList<>();
		attInfo.add( new Attribute( "area" ) );
		attInfo.add( new Attribute( "perimeter" ) );
		attInfo.add( new Attribute( "convexity" ) );
		attInfo.add( new Attribute( "circularity" ) );
		attInfo.add( new Attribute( "class", Arrays.asList( "bad", "good" ) ) );
		Instances table = new Instances( "foo bar", attInfo, 1 );
		table.setClassIndex( 4 ); //TODO this changes depending on how many features are used, expose this in future maybe
		return table;
	}


	public void initializeTrainingData( ArrayList< LabelingSegment > goodHypotheses, ArrayList< LabelingSegment > badHypotheses ) {
		trainingData = newTable();
		for ( int i = 0; i < goodHypotheses.size(); i++ ) {
			DenseInstance ins = extractFeaturesFromHypotheses( goodHypotheses.get( i ), 1 );
			trainingData.add( ins );
		}
		for ( int i = 0; i < badHypotheses.size(); i++ ) {
			DenseInstance ins = extractFeaturesFromHypotheses( badHypotheses.get( i ), 0 );
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

	public Map< LabelingSegment, Double > predict( LabelingFrames labelingFrames ) {
		Map< LabelingSegment, Double > costs = new HashMap<>();
		Instances testData = newTable();
		for ( int t = 0; t < labelingFrames.getNumFrames(); t++ ) {
			for ( final LabelingSegment segment : labelingFrames.getSegments( t ) ) {
				DenseInstance ins = extractFeaturesFromHypotheses( segment, 0 );
				ins.setDataset( testData );
				try {
					double prob = forest.distributionForInstance( ins )[1];
					costs.put( segment, -prob );
					
				} catch ( Exception e ) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		return costs;
	}

	private DenseInstance extractFeaturesFromHypotheses( LabelingSegment hypothesis, int category ) {
		Polygon2D poly = ops.geom().contour( ( RandomAccessibleInterval ) hypothesis.getRegion(), true );
		double perimeter = ops.geom().boundarySize( poly ).get();
		double convexity = ops.geom().convexity( poly ).get();
		double circularity = ops.geom().circularity( poly ).get();
		DenseInstance ins = new DenseInstance( 1.0, new double[] { hypothesis.getArea(), perimeter, convexity, circularity, category } );
		return ins;
	}

}
