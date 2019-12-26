/**
 *
 */
package com.indago.metaseg.ui.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

import com.indago.costs.CostFactory;
import com.indago.costs.CostParams;
import com.indago.data.segmentation.ConflictGraph;
import com.indago.data.segmentation.LabelingSegment;
import com.indago.io.ProjectFolder;
import com.indago.metaseg.MetaSegLog;
import com.indago.metaseg.data.LabelingFrames;
import com.indago.metaseg.io.projectfolder.MetasegProjectFolder;
import com.indago.metaseg.randomforest.MetaSegRandomForestClassifier;
import com.indago.ui.bdv.BdvOwner;
import com.indago.util.ImglibUtil;

import bdv.util.BdvHandlePanel;
import bdv.util.BdvOverlay;
import bdv.util.BdvSource;
import ij.ImagePlus;
import net.imagej.ImgPlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

/**
 * @author jug
 */
public class MetaSegCostPredictionTrainerModel implements CostFactory< LabelingSegment >, BdvOwner {

	private final MetaSegModel parentModel;
	private LabelingFrames goodLabelingFrames;
	private LabelingFrames badLabelingFrames;
	private LabelingFrames predictionLabelingFrames;

	private Map< LabelingSegment, Double > costs;

	private BdvHandlePanel bdvHandlePanel;
	private final List< RandomAccessibleInterval< IntType > > imgs = new ArrayList<>();
	private final List< BdvSource > bdvSources = new ArrayList<>();
	private final List< BdvOverlay > overlays = new ArrayList<>();
	private List< ValuePair< LabelingSegment, Integer > > goodHypotheses; // Labeling segment with time index
	private List< ValuePair< LabelingSegment, Integer > > badHypotheses; // Labeling segment with time index
	private List< ValuePair< LabelingSegment, Integer > > predictionSet; // Labeling segment with time index
	private List< ValuePair< LabelingSegment, Integer > > allSegsWithTime; // Labeling segment with time index
	private int maxHypothesisSize = 30000; // gets set to more sensible value in constructor
	private int minHypothesisSize = 100;
	private MetaSegRandomForestClassifier rf;
	private DoubleType min;
	private DoubleType max;

	private final String FOLDER_LABELING_FRAMES = "labeling_frames";
	private final String FILENAME_PGRAPH = "metaseg_problem.pg";
	private final ProjectFolder dataFolder;
	private boolean savedCostsLoaded;


	public MetaSegCostPredictionTrainerModel( final MetaSegModel metaSegModel ) {
		parentModel = metaSegModel;
		costs = new HashMap<>();
		ImgPlus< DoubleType > img = parentModel.getRawData();
		min = img.randomAccess().get().copy();
		max = min.copy();
		ImglibUtil.computeMinMax( Views.iterable( img ), min, max );
		final ImagePlus temp = ImageJFunctions.wrap( metaSegModel.getRawData(), "raw.tif" );
		this.maxHypothesisSize = temp.getWidth() * temp.getHeight() - 1;
		dataFolder = metaSegModel.getProjectFolder().getFolder( MetasegProjectFolder.LABELING_FRAMES_FOLDER );
		dataFolder.mkdirs();
		this.goodLabelingFrames =
				new LabelingFrames( metaSegModel.getSegmentationModel(), this.getMinPixelComponentSize(), this.getMaxPixelComponentSize() );
		this.badLabelingFrames =
				new LabelingFrames( metaSegModel.getSegmentationModel(), this.getMinPixelComponentSize(), this.getMaxPixelComponentSize() );
		this.predictionLabelingFrames =
				new LabelingFrames( metaSegModel.getSegmentationModel(), this.getMinPixelComponentSize(), this.getMaxPixelComponentSize() );
//		loadStoredLabelingFramesAndSegmentCosts();

	}


	public MetaSegModel getParentModel() {
		return parentModel;
	}

	public void createLabelingsFromScratch( String quality ) {
		if ( quality == "good" ) {
			goodLabelingFrames = new LabelingFrames( parentModel.getSegmentationModel(), 1, Integer.MAX_VALUE );
			goodLabelingFrames.setMaxSegmentSize( maxHypothesisSize );
			goodLabelingFrames.setMinSegmentSize( minHypothesisSize );
			MetaSegLog.log.info( "...processing good LabelFrame inputs..." );
			goodLabelingFrames.processFrames( quality );
		} else if ( quality == "bad" ) {
			badLabelingFrames = new LabelingFrames( parentModel.getSegmentationModel(), 1, Integer.MAX_VALUE );
			badLabelingFrames.setMaxSegmentSize( maxHypothesisSize );
			badLabelingFrames.setMinSegmentSize( minHypothesisSize );
			MetaSegLog.log.info( "...processing bad LabelFrame inputs..." );
			badLabelingFrames.processFrames( quality );
		} else if ( quality == "pred" ) {
			predictionLabelingFrames = new LabelingFrames( parentModel.getSegmentationModel(), 1, Integer.MAX_VALUE );
			predictionLabelingFrames.setMaxSegmentSize( maxHypothesisSize );
			predictionLabelingFrames.setMinSegmentSize( minHypothesisSize );
			MetaSegLog.log.info( "...processing prediction LabelFrame inputs..." );
			predictionLabelingFrames.processFrames( quality );
		}

	}

	public LabelingFrames getAlreadyExistingLabelingFrames() {
		return predictionLabelingFrames;
	}

	/**
	 * @param maxPixelComponentSize
	 *            the maximum size (in pixels) a component can be in order
	 *            to count as a valid segmentation hypothesis.
	 */
	public void setMaxPixelComponentSize( int maxValue ) {
		this.maxHypothesisSize = maxValue;
	}

	/**
	 * @param minPixelComponentSize
	 *            the minimum size (in pixels) a component needs to be in order
	 *            to count as a valid segmentation hypothesis.
	 */
	public void setMinPixelComponentSize( int minValue ) {
		this.minHypothesisSize = minValue;
	}

	/**
	 * @return the maximum size (in pixels) a component can be in order
	 *         to count as a valid segmentation hypothesis.
	 */
	public int getMaxPixelComponentSize() {
		return maxHypothesisSize;
	}

	/**
	 * @return the minimum size (in pixels) a component needs to be in order
	 *         to count as a valid segmentation hypothesis.
	 */
	public int getMinPixelComponentSize() {
		return minHypothesisSize;
	}

	public ConflictGraph< LabelingSegment > getConflictGraph( final int t ) {
		return predictionLabelingFrames.getConflictGraph( t );
	}

	public List< ConflictGraph< LabelingSegment > > getConflictGraphs() {
		final List< ConflictGraph< LabelingSegment > > ret = new ArrayList<>();
		for ( int t = 0; t < predictionLabelingFrames.getNumFrames(); t++ ) {
			ret.add( getConflictGraph( t ) );
		}
		return ret;
	}

	public Collection< ? extends Collection< LabelingSegment > > getConflictCliques( final int t ) {
		return predictionLabelingFrames.getConflictGraph( t ).getConflictGraphCliques();
	}

	public List< Collection< ? extends Collection< LabelingSegment > > > getConflictCliques() {
		final List< Collection< ? extends Collection< LabelingSegment > > > ret = new ArrayList<>();
		for ( int t = 0; t < predictionLabelingFrames.getNumFrames(); t++ ) {
			ret.add( getConflictCliques( t ) );
		}
		return ret;
	}

	public boolean hasCost( final LabelingSegment ls ) {
		return costs.containsKey( ls );
	}

	/**
	 * @see com.indago.costs.CostFactory#getCost(java.lang.Object)
	 */
	@Override
	public double getCost( final LabelingSegment ls ) {
		return costs.get( ls );
	}

	/**
	 * @see com.indago.costs.CostFactory#getParameters()
	 */
	@Override
	public CostParams getParameters() {
		return null;
	}

	/**
	 * @see com.indago.costs.CostFactory#setParameters(com.indago.costs.CostParams)
	 */
	@Override
	public void setParameters( final CostParams p ) {
		MetaSegLog.solverLog.error( "No parameters are accepted for this CostFactory!" );
	}

	@Override
	public BdvHandlePanel bdvGetHandlePanel() {
		return bdvHandlePanel;
	}

	@Override
	public void bdvSetHandlePanel( BdvHandlePanel bdvHandlePanel ) {
		this.bdvHandlePanel = bdvHandlePanel;
	}

	@Override
	public List< BdvSource > bdvGetSources() {
		return this.bdvSources;
	}


	public void registerKeyBinding( KeyStroke keyStroke, String name, Action action ) {

		InputMap im = bdvHandlePanel.getViewerPanel().getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW );
		ActionMap am = bdvHandlePanel.getViewerPanel().getActionMap();

		im.put( keyStroke, name );
		am.put( name, action );
	}

	public void populateBdv() {
		bdvRemoveAll();

		imgs.clear();
		overlays.clear();

		bdvAdd( parentModel.getRawData(), "RAW" );
	}

	public void randomizeSegmentsAndPrepData() {
		List< ValuePair< LabelingSegment, Integer > > temp = new ArrayList<>( allSegsWithTime );
		List< ValuePair< LabelingSegment, Integer > > randomizedSegsWIthTime = getAllSegmentsRandomized( temp );
		predictionSet = new ArrayList<>( randomizedSegsWIthTime );
		goodHypotheses = new ArrayList< ValuePair< LabelingSegment, Integer > >();
		badHypotheses = new ArrayList< ValuePair< LabelingSegment, Integer > >();
	}

	public List< ValuePair< LabelingSegment, Integer > > getAllSegsWithIdAndTime() {
		List< List< LabelingSegment > > allSegsAllTime = new ArrayList< List< LabelingSegment > >( predictionLabelingFrames.getSegments() );
		List< ValuePair< LabelingSegment, Integer > > allSegsWithIdAndTime = new ArrayList<>();
		for ( int time = 0; time < allSegsAllTime.size(); time++ ) {
			for ( LabelingSegment ls : allSegsAllTime.get( time ) ) {
				allSegsWithIdAndTime.add( new ValuePair< LabelingSegment, Integer >( ls, time ) );
			}
		}
		return allSegsWithIdAndTime;
	}

	private List< ValuePair< LabelingSegment, Integer > > getAllSegmentsRandomized(
			List< ValuePair< LabelingSegment, Integer > > temp ) {
		List< ValuePair< LabelingSegment, Integer > > randomizedSegsWIthTime = new ArrayList<>( temp );
		Collections.shuffle( randomizedSegsWIthTime );
		return randomizedSegsWIthTime;
	}

	public void startTrainingPhase() throws Exception {
		if ( !( goodHypotheses.isEmpty() ) || !( badHypotheses.isEmpty() ) ) {
			extractFeatures();
		}

	}

	public List< ValuePair< LabelingSegment, Integer > > getAllSegsWithTime() {
		return allSegsWithTime;
	}

	public void addToGood( ValuePair< LabelingSegment, Integer > segment ) {
		goodHypotheses.add( segment );
	}

	public void addToBad( ValuePair< LabelingSegment, Integer > segment ) {
		badHypotheses.add( segment );
	}

	private void extractFeatures() throws Exception {
		rf = new MetaSegRandomForestClassifier( parentModel.is2D(), parentModel );
		rf.buildRandomForest();
		ArrayList< ValuePair< LabelingSegment, Integer > > goodSegs = new ArrayList<>();
		ArrayList< ValuePair< LabelingSegment, Integer > > badSegs = new ArrayList<>();
		goodSegs.addAll( goodHypotheses );
		badSegs.addAll( badHypotheses );
		rf.initializeTrainingData( goodSegs, badSegs );
		trainForest( rf );
	}

	private void trainForest( MetaSegRandomForestClassifier rf ) throws Exception {
		rf.train();
	}

	public Map< LabelingSegment, Double > computeAllCosts() {
		ArrayList< ValuePair< LabelingSegment, Integer > > predSetThisIter = new ArrayList<>();
		for ( ValuePair< LabelingSegment, Integer > valuePair : predictionSet ) {
			predSetThisIter.add( valuePair );
		}
		costs = rf.predict( predSetThisIter );
		for ( ValuePair< LabelingSegment, Integer > segment : goodHypotheses ) {
			costs.put( segment.getA(), -10d );
		}
		for ( ValuePair< LabelingSegment, Integer > segment : badHypotheses ) {
			costs.put( segment.getA(), 100d ); //Setting positive costs (aggressive) instead of 0 to ensure bad hypotheses are never selected by optimization
		}
		System.out.println( "Size of costs updated:" + costs.size() );

		return costs;
	}

	@Override
	public < T extends RealType< T > & NativeType< T > > BdvSource bdvGetSourceFor( RandomAccessibleInterval< T > img ) {
		return null;
	}

	public void setAllSegAndCorrespTime() {
		allSegsWithTime = new ArrayList<>();
		allSegsWithTime = getAllSegsWithIdAndTime();
	}

	public void purgeSegmentationData() { //TODO Inspect again
		parentModel.getProjectFolder().getFile( FILENAME_PGRAPH ).getFile().delete();
		try {
			dataFolder.getFolder( FOLDER_LABELING_FRAMES ).deleteContent();
		} catch ( final IOException e ) {
			if ( dataFolder.getFolder( FOLDER_LABELING_FRAMES ).exists() ) {
				MetaSegLog.log.error( "Labeling frames exist but cannot be deleted." );
			}
		}
		if ( !costs.isEmpty() ) {
			clearAllCosts();
		}

	}

	public boolean isSavedCostsLoaded() {
		return savedCostsLoaded;
	}

	public void setSavedCostsLoaded( boolean b ) {
		savedCostsLoaded = b;
	}

	public boolean isCostsExists() {
		if ( costs.isEmpty() ) {
			return false;
		} else {
			return true;
		}
	}

	public void clearAllCosts() {
		costs.clear();
	}

	public void populateGoodHypothesesList() {

		for ( int frame = 0; frame < parentModel.getNumberOfFrames(); frame++ ) {
			List< LabelingSegment > temp = goodLabelingFrames.getLabelingSegmentsForFrame( frame );
			for ( LabelingSegment labelingSegment : temp ) {
				goodHypotheses.add( new ValuePair< LabelingSegment, Integer >( labelingSegment, frame ) );
			}
		}

	}

	public void populateBadHypothesesList() {

		for ( int frame = 0; frame < parentModel.getNumberOfFrames(); frame++ ) {
			List< LabelingSegment > temp = badLabelingFrames.getLabelingSegmentsForFrame( frame );
			for ( LabelingSegment labelingSegment : temp ) {
				badHypotheses.add( new ValuePair< LabelingSegment, Integer >( labelingSegment, frame ) );
			}
		}

	}

}
