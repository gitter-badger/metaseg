package com.indago.metaseg.threadedfeaturecomputation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.indago.data.segmentation.LabelingSegment;
import com.indago.metaseg.ui.model.MetaSegModel;
import com.indago.metaseg.ui.view.FeatureSelection;

import net.imglib2.parallel.TaskExecutor;
import net.imglib2.parallel.TaskExecutors;
import net.imglib2.util.ValuePair;

/**
 * Calculates the {@link FeaturesRow} for a given {@link LabelingSegment}.
 */
public class MetaSegSegmentFeatureComputation implements Runnable {

	private final MetaSegModel parentModel;
	private Map< LabelingSegment, FeaturesRow > featuresTable = new ConcurrentHashMap< LabelingSegment, FeaturesRow >();

	private List< ValuePair< LabelingSegment, Integer > > hypothesesSet;
	private final MetaSegSingleSegmentFeatureComputation singleFeatureComputerObject;
	private FeatureSelection featureSelection = new FeatureSelection();

	public MetaSegSegmentFeatureComputation( final MetaSegModel model, List< ValuePair< LabelingSegment, Integer > > predictionSet ) {
		parentModel = model;
		this.hypothesesSet = predictionSet;
		this.singleFeatureComputerObject = new MetaSegSingleSegmentFeatureComputation( parentModel );
	}

	public FeatureSelection getFeatureSelection() {
		return featureSelection;
	}

	public void setFeatureSelection( FeatureSelection fs ) {
		this.featureSelection = fs;
		singleFeatureComputerObject.setFeatureSelection( featureSelection );
		featuresTable.clear();
	}

	public FeaturesRow getFeatureRow( ValuePair< LabelingSegment, Integer > valuePair )
	{
		FeaturesRow featureRow = featuresTable.get( valuePair.getA() );
		if(featureRow == null) {
			MetaSegSingleSegmentFeatureComputation singleFeatureComputerObject = new MetaSegSingleSegmentFeatureComputation( parentModel );
			singleFeatureComputerObject.setFeatureSelection( featureSelection );
			featureRow = singleFeatureComputerObject.extractFeaturesFromHypothesis( valuePair );
			featuresTable.put( valuePair.getA(), featureRow );
		}
		return featureRow;
	}

	@Override
	public final void run() {
		TaskExecutor taskExecutor = TaskExecutors.fixedThreadPool( Runtime.getRuntime().availableProcessors() );
		taskExecutor.forEach( hypothesesSet, segment -> extractFeaturesFromHypothesis( segment ) );
	}

	private void extractFeaturesFromHypothesis( ValuePair< LabelingSegment, Integer > valuePair ) {
		FeaturesRow featureRow = singleFeatureComputerObject.extractFeaturesFromHypothesis( valuePair );
		featuresTable.put( valuePair.getA(), featureRow);
	}

}
