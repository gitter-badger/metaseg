package com.indago.metaseg.randomforest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.indago.data.segmentation.LabelingSegment;
import com.indago.metaseg.threadedfeaturecomputation.FeaturesRow;
import com.indago.metaseg.threadedfeaturecomputation.MetaSegSegmentFeatureComputation;
import com.indago.metaseg.threadedfeaturecomputation.MetaSegSingleSegmentFeatureComputation;
import com.indago.metaseg.ui.model.MetaSegModel;
import com.indago.metaseg.ui.util.ClassifierLoaderAndSaver;

import hr.irb.fastRandomForest.FastRandomForest;
import net.imglib2.util.ValuePair;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

public class MetaSegRandomForestClassifier {

	private final MetaSegModel model;

	public MetaSegRandomForestClassifier( boolean is2D, MetaSegModel model ) {
		this.model = model;
	}

	private Instances trainingData;
	private FastRandomForest forest;
	
	private static Instances newTable() {
		ArrayList< Attribute > attInfo = new ArrayList<>();
		attInfo.add( new Attribute( "area" ) );
		attInfo.add( new Attribute( "perimeter" ) );
		attInfo.add( new Attribute( "convexity" ) );
		attInfo.add( new Attribute( "circularity" ) );
		attInfo.add( new Attribute( "solidity" ) );
		attInfo.add( new Attribute( "boundarysizeconvexhull" ) );
//		attInfo.add( new Attribute( "elongation" ) );
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
			DenseInstance ins = getPrecomputedHypothesisFeatures( goodHypotheses.get( i ), 1, 1 );
			trainingData.add( ins );
		}
		for ( int i = 0; i < badHypotheses.size(); i++ ) {
			DenseInstance ins = getPrecomputedHypothesisFeatures( badHypotheses.get( i ), relative_weight_bad, 0 );
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

	public double predictSegmentProbability( ValuePair< LabelingSegment, Integer > segment ) throws Exception {
		Instances testData = newTable();
		DenseInstance ins = getPrecomputedHypothesisFeatures( segment, 1, 0 );
		ins.setDataset( testData );
		double prob = forest.distributionForInstance( ins )[ 1 ];
		return prob; //This returns the probability of segment belonging to good class
	}

	private DenseInstance getPrecomputedHypothesisFeatures( ValuePair< LabelingSegment, Integer > valuePair, double weight, int category ) {

		MetaSegSegmentFeatureComputation computeAllFeaturesObject = model.getCostTrainerModel().getComputeAllFeaturesObject();
		Map< LabelingSegment, FeaturesRow > featuresTable = computeAllFeaturesObject.getFeaturesTable();
		FeaturesRow segmentFeatures = featuresTable.get( valuePair.getA() );
		if(segmentFeatures == null) {
			MetaSegSingleSegmentFeatureComputation singleFeatureComputerObject = new MetaSegSingleSegmentFeatureComputation( model );
			segmentFeatures = singleFeatureComputerObject.extractFeaturesFromHypothesis( valuePair );
			featuresTable.put( valuePair.getA(), segmentFeatures );
		}
		DenseInstance ins = new DenseInstance( weight, new double[] { segmentFeatures.getArea(),
		                                                              segmentFeatures.getPerimeter(),
		                                                              segmentFeatures.getConvexity(),
		                                                              segmentFeatures.getCircularity(),
		                                                              segmentFeatures.getSolidity(),
		                                                              segmentFeatures.getBoundarysizeconvexhull(),
//																	  segmentFeatures.getElongation(),
		                                                              segmentFeatures.getNormalizedBoundaryPixelSum(),
		                                                              segmentFeatures.getNormalizedFacePixelSum(),
																	  category } );
		return ins;
	}

	public boolean saveRandomForest() {
		boolean classifierSavedFlag = false;
		if ( !( forest == null ) ) {
			classifierSavedFlag =
					ClassifierLoaderAndSaver
					.saveRandomForestClassifier( forest, model.getProjectFolder().getFolder().getPath() );
		}
		return classifierSavedFlag;
	}

	public void loadRandomForest() {
		forest = ClassifierLoaderAndSaver.loadRandomForestClassifier( model.getProjectFolder().getFolder().getPath() );
	}

	public boolean isRandomForestExists() {
		return (!(forest==null) ? true : false);
	}

}
