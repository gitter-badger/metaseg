package com.indago.metaseg.threadedfeaturecomputation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.indago.data.segmentation.LabelingSegment;
import com.indago.metaseg.ui.model.MetaSegModel;

import net.imagej.ImgPlus;
import net.imglib2.parallel.TaskExecutor;
import net.imglib2.parallel.TaskExecutors;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.ValuePair;

public class MetaSegSegmentFeatureComputation implements Runnable {

	private final MetaSegModel parentModel;
	private ImgPlus< DoubleType > img;
	private Map< LabelingSegment, FeaturesRow > featuresTable = new ConcurrentHashMap< LabelingSegment, FeaturesRow >();

	private List< ValuePair< LabelingSegment, Integer > > hypothesesSet;
	private final MetaSegSingleSegmentFeatureComputation singleFeatureComputerObject;

	public MetaSegSegmentFeatureComputation( final MetaSegModel model, List< ValuePair< LabelingSegment, Integer > > predictionSet ) {
		parentModel = model;
		img = model.getRawData();
		this.hypothesesSet = predictionSet;
		this.singleFeatureComputerObject = new MetaSegSingleSegmentFeatureComputation( parentModel );
	}

	@Override
	public final void run() {
		TaskExecutor taskExecutor = TaskExecutors.fixedThreadPool( Runtime.getRuntime().availableProcessors() );
		taskExecutor.forEach( hypothesesSet, segment -> extractFeaturesFromHypothesis( segment ) );
	}

	public Map< LabelingSegment, FeaturesRow > getFeaturesTable() {
		return featuresTable;
	}

	private void extractFeaturesFromHypothesis( ValuePair< LabelingSegment, Integer > valuePair ) {
		FeaturesRow featureRow = singleFeatureComputerObject.extractFeaturesFromHypothesis( valuePair );
		featuresTable.put( valuePair.getA(), featureRow);
	}

}
