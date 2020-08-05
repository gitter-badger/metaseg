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
public class MetaSegSegmentFeatureComputation {

	private final MetaSegModel parentModel;
	private final Map< LabelingSegment, FeaturesRow > featuresTable = new ConcurrentHashMap<>();
	private final List< ValuePair< LabelingSegment, Integer > > hypothesesSet;
	private final ThreadLocal< MetaSegSingleSegmentFeatureComputation > featureComputers;
	private final FeatureSelection featureSelection;
	private boolean cancel = false;

	public MetaSegSegmentFeatureComputation( final MetaSegModel model, List< ValuePair< LabelingSegment, Integer > > predictionSet,
			FeatureSelection featureSelection) {
		this.parentModel = model;
		this.featureSelection = featureSelection;
		this.hypothesesSet = predictionSet;
		this.featureComputers = ThreadLocal.withInitial(() -> new MetaSegSingleSegmentFeatureComputation(parentModel, featureSelection));
	}

	public FeaturesRow getFeatureRow( ValuePair< LabelingSegment, Integer > valuePair )
	{
		FeaturesRow featureRow = featuresTable.get( valuePair.getA() );
		if(featureRow == null) {
			extractFeaturesFromHypothesis(valuePair);
		}
		return featuresTable.get( valuePair.getA() );
	}

	public void computeAllFeatureInBackground(Runnable completionCallback) {
		Thread featureComputerThread = new Thread( () -> {
			TaskExecutor taskExecutor = TaskExecutors.fixedThreadPool( Runtime.getRuntime().availableProcessors() );
			taskExecutor.forEach( hypothesesSet, segment -> {
				if(cancel)
					return;
				extractFeaturesFromHypothesis( segment );
			} );
			if(cancel)
				return;
			completionCallback.run();
		} );
		featureComputerThread.setPriority( Thread.MIN_PRIORITY );
		featureComputerThread.start();
	}

	private void extractFeaturesFromHypothesis( ValuePair< LabelingSegment, Integer > valuePair ) {
		FeaturesRow featureRow = featureComputers.get().extractFeaturesFromHypothesis( valuePair );
		featuresTable.put( valuePair.getA(), featureRow);
	}

	public FeatureSelection getFeatureSelection() {
		return featureSelection;
	}

	public void cancel() {
		this.cancel = true;
	}
}
