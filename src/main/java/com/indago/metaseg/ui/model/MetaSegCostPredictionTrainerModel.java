/**
 *
 */
package com.indago.metaseg.ui.model;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Stack;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

import com.indago.costs.CostFactory;
import com.indago.costs.CostParams;
import com.indago.data.segmentation.ConflictGraph;
import com.indago.data.segmentation.LabelingSegment;
import com.indago.io.DataMover;
import com.indago.io.ProjectFile;
import com.indago.io.ProjectFolder;
import com.indago.metaseg.MetaSegLog;
import com.indago.metaseg.data.LabelingFrames;
import com.indago.metaseg.io.projectfolder.MetasegProjectFolder;
import com.indago.metaseg.randomforest.MetaSegRandomForestClassifier;
import com.indago.metaseg.ui.util.Utils;
import com.indago.ui.bdv.BdvOwner;
import com.indago.util.ImglibUtil;

import bdv.util.BdvHandlePanel;
import bdv.util.BdvOverlay;
import bdv.util.BdvSource;
import ij.ImagePlus;
import indago.ui.progress.ProgressListener;
import net.imagej.ImgPlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.roi.IterableRegion;
import net.imglib2.roi.Regions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/**
 * @author jug
 */
public class MetaSegCostPredictionTrainerModel implements CostFactory< LabelingSegment >, BdvOwner {

	private final MetaSegModel parentModel;
	private LabelingFrames labelingFrames;

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
	public String alMode;
	private Stack< Pair< ValuePair< LabelingSegment, Integer >, String > > undoStack = new Stack<>(); //Stores labeling segments with time and their good/bad class for undo operation
	private boolean continuousRetrainState;
	private boolean ongoingUndoFlag = false;
	private Stack< Pair< ValuePair< LabelingSegment, Integer >, String > > undoHelperStack = new Stack<>();
	private Pair< ValuePair< LabelingSegment, Integer >, String > poppedStatePair; // Labeling segment with time index
	private DoubleType min;
	private DoubleType max;

	private final String FOLDER_LABELING_FRAMES = "labeling_frames";
	private final String FILENAME_PGRAPH = "metaseg_problem.pg";
	private final ProjectFolder dataFolder;
	private ProjectFolder hypothesesFolder;
	private final List< ProgressListener > progressListeners = new ArrayList<>();
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
		this.labelingFrames =
				new LabelingFrames( metaSegModel.getSegmentationModel(), this.getMinPixelComponentSize(), this.getMaxPixelComponentSize() );
		loadStoredLabelingFramesAndSegmentCosts();

	}


	public MetaSegModel getParentModel() {
		return parentModel;
	}

	public LabelingFrames getLabelingsAfterCreationFromScratch() {
		if ( this.labelingFrames == null ) {
			labelingFrames = new LabelingFrames( parentModel.getSegmentationModel(), 1, Integer.MAX_VALUE );
			labelingFrames.setMaxSegmentSize( maxHypothesisSize );
			labelingFrames.setMinSegmentSize( minHypothesisSize );
			MetaSegLog.log.info( "...processing LabelFrame inputs..." );
			labelingFrames.processFrames();
		} else {
			MetaSegLog.log.info( "...processing LabelFrame inputs..." );
			labelingFrames.processFrames();
		}

		return labelingFrames;
	}

	public LabelingFrames getAlreadyExistingLabelingFrames() {
		return labelingFrames;
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

	public void setALMode( String mode ) {
		alMode = mode;
	}

	public ConflictGraph< LabelingSegment > getConflictGraph( final int t ) {
		return labelingFrames.getConflictGraph( t );
	}

	public List< ConflictGraph< LabelingSegment > > getConflictGraphs() {
		final List< ConflictGraph< LabelingSegment > > ret = new ArrayList<>();
		for ( int t = 0; t < labelingFrames.getNumFrames(); t++ ) {
			ret.add( getConflictGraph( t ) );
		}
		return ret;
	}

	public Collection< ? extends Collection< LabelingSegment > > getConflictCliques( final int t ) {
		return labelingFrames.getConflictGraph( t ).getConflictGraphCliques();
	}

	public List< Collection< ? extends Collection< LabelingSegment > > > getConflictCliques() {
		final List< Collection< ? extends Collection< LabelingSegment > > > ret = new ArrayList<>();
		for ( int t = 0; t < labelingFrames.getNumFrames(); t++ ) {
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

	public void chooseGoodBadAndModifyTrainPredUndoSets( LabelingSegment labelingSegment ) {
		registerKeyBinding( KeyStroke.getKeyStroke( KeyEvent.VK_Y, 0 ), "Yes", new AbstractAction() {

			@Override
			public void actionPerformed( ActionEvent e ) {

				modifyStatesIfUndo();
				ValuePair< LabelingSegment, Integer > vp =
						new ValuePair<>( labelingSegment, bdvHandlePanel.getViewerPanel().getState().getCurrentTimepoint() );
				goodHypotheses.add( vp );
				undoStack.push( new ValuePair<>( vp, "good" ) );
				modifyPredictionSet();
				MetaSegLog.log.info( "Added as good segment!" );
				try {
					selectSegmentForDisplay();
				} catch ( Exception e1 ) {
					e1.printStackTrace();
				}

			}

		} );

		registerKeyBinding( KeyStroke.getKeyStroke( KeyEvent.VK_N, 0 ), "No", new AbstractAction() {

			@Override
			public void actionPerformed( ActionEvent e ) {
				modifyStatesIfUndo();
				ValuePair< LabelingSegment, Integer > vp =
						new ValuePair<>( labelingSegment, bdvHandlePanel.getViewerPanel().getState().getCurrentTimepoint() );
				badHypotheses.add( vp );
				undoStack.push( new ValuePair<>( vp, "bad" ) );
				modifyPredictionSet();
				MetaSegLog.log.info( "Added as bad segment!" );
				try {
					selectSegmentForDisplay();
				} catch ( Exception e1 ) {
					e1.printStackTrace();
				}
			}

		} );
	};


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

	private List< ValuePair< LabelingSegment, Integer > > getAllSegsWithIdAndTime() {
		List< List< LabelingSegment > > allSegsAllTime = new ArrayList< List< LabelingSegment > >( labelingFrames.getSegments() );
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

	public void selectSegmentForDisplay() throws Exception { //safeguard against size of pred set = 0 needs checking
		if ( goodHypotheses.size() < 1 || badHypotheses.size() < 1 ) {
			//No need to do intermediate prediction as if there is no instance of one class, everything will be predicted to other class
			displaySelectedSegment( predictionSet.get( 0 ) );
		} else {
			if ( continuousRetrainState ) {
				startTrainingPhase();
				double uncertaintyLB;
				double uncertaintyUB;
				ValuePair< LabelingSegment, Integer > chosenSegWIthTime = null;
				if ( alMode == "active learning (normal)" ) { //TODO fix for positive costs 
					uncertaintyLB = -0.8d;
					uncertaintyUB = -0.2d;
					chosenSegWIthTime = pickUncertianSegmentIteratively( uncertaintyLB, uncertaintyUB );
				} else if ( alMode == "active learning (class balance)" ) {
					if ( goodHypotheses.size() >= badHypotheses.size() ) {
//						uncertaintyLB = -0.49d;
//						uncertaintyUB = -0.2d;
						uncertaintyLB = 0.2d;
						uncertaintyUB = 0.49d;
					} else {
						uncertaintyLB = -0.8d;
						uncertaintyUB = -0.51d;
					}
					chosenSegWIthTime = pickUncertianSegmentIteratively( uncertaintyLB, uncertaintyUB );
				} else if ( alMode == "random" ) {
					List< ValuePair< LabelingSegment, Integer > > allSegments = new ArrayList<>( predictionSet );
					if ( allSegments.isEmpty() ) {
						System.out.println( "Empty!" );
					}
					Random rand = new Random();
					int n = rand.nextInt( allSegments.size() );
					chosenSegWIthTime = allSegments.get( n );
				}
				displaySelectedSegment( chosenSegWIthTime );
			} else {
				if ( !predictionSet.isEmpty() ) {
					displaySelectedSegment( predictionSet.get( 0 ) );
				} else {
					JOptionPane.showMessageDialog( null, "Finished classifying all hypotheses..." );
				}

			}

		}
	}

	private void displaySelectedSegment( ValuePair< LabelingSegment, Integer > chosenSegWithTime ) { //Can be removed later or modified to accommodate displaynextSegment()
		showSeg( chosenSegWithTime );
	}

	private void showSeg( ValuePair< LabelingSegment, Integer > chosenSegWIthTime ) {
		int maxVal = 2;
		bdvRemoveAll();
		bdvAdd( parentModel.getRawData(), "RAW", min.get(), max.get(), new ARGBType( 0xFFFFFF ), true );
		final int c = 1;
		final RandomAccessibleInterval< IntType > hypothesisImage = DataMover.createEmptyArrayImgLike( parentModel.getRawData(), new IntType() );
		LabelingSegment segment = chosenSegWIthTime.getA();
		int time = chosenSegWIthTime.getB();
		paintSegmentToDisplayDuringManualClassification( c, hypothesisImage, segment, time );
		bdvHandlePanel.getViewerPanel().setTimepoint( time );
		setZSlice( segment ); //only for 3d
		int displayColor = chooseColorForDisplay();
		bdvAdd( hypothesisImage, "Classifying", 0, maxVal, new ARGBType( displayColor ), true );
		chooseGoodBadAndModifyTrainPredUndoSets( segment );
	}

	private int chooseColorForDisplay() {
		int displayColor;
		if ( ongoingUndoFlag ) {
			if ( poppedStatePair.getB() == "bad" ) {
				displayColor = 0xFF0000;
			} else {
				displayColor = 0x00FF00;
			}
		} else {
			displayColor = 0xFFD700;
		}
		return displayColor;
	}

	private void paintSegmentToDisplayDuringManualClassification(
			final int c,
			final RandomAccessibleInterval< IntType > hypothesisImage,
			LabelingSegment segment,
			int time ) {
		IterableRegion< ? > region = segment.getRegion();
		IntervalView< IntType > retSlice;

		if ( parentModel.getNumberOfFrames() > 1 ) {
			retSlice =
					Views.hyperSlice(
							hypothesisImage,
							parentModel.getTimeDimensionIndex(),
							time );
		} else {
			long[] mininterval = new long[ hypothesisImage.numDimensions() ];
			long[] maxinterval = new long[ hypothesisImage.numDimensions() ];
			for ( int i = 0; i < mininterval.length - 1; i++ ) {
				mininterval[ i ] = hypothesisImage.min( i );
				maxinterval[ i ] = hypothesisImage.max( i );
			}
			retSlice = Views.interval( hypothesisImage, mininterval, maxinterval );
		}

		try {
			Regions.sample( region, retSlice ).forEach( t -> t.set( c ) );

		} catch ( final ArrayIndexOutOfBoundsException aiaob ) {
			MetaSegLog.log.error( aiaob );
		}
	}

	private void setZSlice( LabelingSegment segment ) {
		if ( parentModel.is2D() == false ) { //only for 3d
			final AffineTransform3D transform = new AffineTransform3D();
			bdvHandlePanel.getViewerPanel().getState().getViewerTransform( transform );
			RealLocalizable source = segment.getCenterOfMass();
			RealPoint target = new RealPoint( 3 );
			transform.apply( source, target );
			transform.translate( 0, 0, -target.getDoublePosition( 2 ) );
			bdvHandlePanel.getViewerPanel().setCurrentViewerTransform( transform );
		}
	}

	private ValuePair< LabelingSegment, Integer > pickUncertianSegmentIteratively( double uncertaintyLB, double uncertaintyUB ) { //safeguard against size of pred set = 0 needs checking
		int[] potentIds = Utils.uniqueRand( ( int ) ( 0.1 * predictionSet.size() ), predictionSet.size() );
		int id = 0;
		double cost;

		ValuePair< LabelingSegment, Integer > hypothesis = new ValuePair< LabelingSegment, Integer >( null, null );
		do {
			if(id<potentIds.length-1) {
				hypothesis = predictionSet.get( potentIds[ id ] );
				Map< LabelingSegment, Double > segAndCost = computeIntermediateCosts( hypothesis );
				cost = segAndCost.get( hypothesis.getA() );
				id = id + 1;
			} else { //break the loop if id exhausts and then show a random segment
				MetaSegLog.log.info( "Segment for class balance not found, falling back to random mode for this iteration..." );
				displaySelectedSegment( predictionSet.get( 0 ) );
				break;
			}
		}
		while ( !( cost > uncertaintyLB && cost < uncertaintyUB ) );
		return hypothesis;
	}

	private void modifyPredictionSet() {
		predictionSet.removeAll( goodHypotheses );
		predictionSet.removeAll( badHypotheses );
	}

	public void startTrainingPhase() throws Exception {
		if ( !( goodHypotheses.isEmpty() ) || !( badHypotheses.isEmpty() ) ) {
			extractFeatures();
		}

	}

	private void extractFeatures() throws Exception {
		rf = new MetaSegRandomForestClassifier( parentModel.is2D() );
		rf.buildRandomForest();
		ArrayList< LabelingSegment > goodSegs = new ArrayList<>();
		ArrayList< LabelingSegment > badSegs = new ArrayList<>();
		for ( ValuePair< LabelingSegment, Integer > vp : goodHypotheses ) {
			goodSegs.add( vp.getA() );
		}
		for ( ValuePair< LabelingSegment, Integer > vp : badHypotheses ) {
			badSegs.add( vp.getA() );
		}
		rf.initializeTrainingData( goodSegs, badSegs );
		trainForest( rf );
	}

	private void trainForest( MetaSegRandomForestClassifier rf ) throws Exception {
		rf.train();
	}

	public Map< LabelingSegment, Double > computeAllCosts() {
		ArrayList< LabelingSegment > predSetThisIter = new ArrayList<>();
		for ( ValuePair< LabelingSegment, Integer > valuePair : predictionSet ) {
			predSetThisIter.add( valuePair.getA() );
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

	public Map< LabelingSegment, Double > computeIntermediateCosts( ValuePair< LabelingSegment, Integer > hypothesis ) {
		ArrayList< LabelingSegment > tempList = new ArrayList<>();
		tempList.add( hypothesis.getA() );
		Map< LabelingSegment, Double > localCosts = rf.predict( tempList );
		int trainsetsize = goodHypotheses.size() + badHypotheses.size();
		System.out.println( "Training set size:" + trainsetsize );
		return localCosts;
	}


	@Override
	public < T extends RealType< T > & NativeType< T > > BdvSource bdvGetSourceFor( RandomAccessibleInterval< T > img ) {
		return null;
	}

	public void showFirstSegmentForManualClassification() {
		JOptionPane
				.showMessageDialog( null, "Starting manual classification step, press Y/N to classify as good/bad hypothesis when displayed..." );
		if ( !predictionSet.isEmpty() ) {
			displaySelectedSegment( predictionSet.get( 0 ) );
		} else {
			JOptionPane.showMessageDialog( null, "No hypotheses to display..." );
		}

	}

	public void setAllSegAndCorrespTime() {
		allSegsWithTime = new ArrayList<>();
		allSegsWithTime = getAllSegsWithIdAndTime();
	}

	public void setContinuousRetrainState( boolean b ) {
		continuousRetrainState = b;
	}

	public void callUndo() {
		if ( !undoStack.isEmpty() ) {
			ongoingUndoFlag = true;

			poppedStatePair = undoStack.pop();
			undoHelperStack.push( poppedStatePair );
			displaySelectedSegment( poppedStatePair.getA() );

		}

	}

	public void modifyStatesIfUndo() {
		if ( ongoingUndoFlag ) {
			String state = poppedStatePair.getB();
			if ( state == "good" ) {
				goodHypotheses.remove( poppedStatePair.getA() );
			} else if ( state == "bad" ) {
				badHypotheses.remove( poppedStatePair.getA() );
			}
			undoHelperStack.pop();
			if ( !undoHelperStack.isEmpty() ) {
				while ( undoHelperStack.size() > 0 ) {
					Pair< ValuePair< LabelingSegment, Integer >, String > noEdit = undoHelperStack.pop();
					undoStack.push( noEdit );
				}
			}
			ongoingUndoFlag = false;
		}

	}

	private void loadStoredLabelingFramesAndSegmentCosts() {
		// Loading hypotheses labeling frames if exist in project folder
		try {
			hypothesesFolder = dataFolder.addFolder( FOLDER_LABELING_FRAMES );
			hypothesesFolder.loadFiles();
			labelingFrames.loadFromProjectFolder( hypothesesFolder );

		} catch ( final IOException ioe ) {
			ioe.printStackTrace();
		}

		// Loading stored PGraph if exists in project folder
		final ProjectFile pgFile = parentModel.getProjectFolder().getFile( FILENAME_PGRAPH );
		if ( pgFile.exists() && pgFile.canRead() ) {
			populateSegmentCostsFromStoredProblemGraphFile( pgFile );
			savedCostsLoaded = true;
		} else {
			savedCostsLoaded = false; //TODO Throw warning that costs are missing.
		}

	}

	private void populateSegmentCostsFromStoredProblemGraphFile( ProjectFile pgFile ) {

		Map< Integer, Double > mapId2Costs = Utils.readProblemGraphFileAndCreateCostIdMap( pgFile );
		for ( int frame = 0; frame < labelingFrames.getNumFrames(); frame++ ) {
			List< LabelingSegment > labelingSegmentsForFrame = labelingFrames.getSegments( frame );
			for ( LabelingSegment labelingSegment : labelingSegmentsForFrame ) {
				Double retrivedCost = mapId2Costs.get( labelingSegment.getId() );
				costs.put( labelingSegment, retrivedCost );
			}
		}

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

	public void saveLabelingFrames() {
		labelingFrames.saveTo( hypothesesFolder, progressListeners );
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

}
