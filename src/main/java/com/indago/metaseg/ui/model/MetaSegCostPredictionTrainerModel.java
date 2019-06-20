/**
 *
 */
package com.indago.metaseg.ui.model;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
import com.indago.metaseg.MetaSegLog;
import com.indago.metaseg.data.LabelingFrames;
import com.indago.metaseg.randomforest.MetaSegRandomForestClassifier;
import com.indago.metaseg.ui.util.Utils;
import com.indago.ui.bdv.BdvOwner;

import bdv.util.BdvHandlePanel;
import bdv.util.BdvOverlay;
import bdv.util.BdvSource;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.roi.IterableRegion;
import net.imglib2.roi.Regions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
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
	private List< LabelingSegment > randomizedAllHypotheses; // Stores all hypotheses in a randomized order
	private List< Integer > randomizedHypothesesTimeIndices; // Stores all hypotheses time points
	private List< LabelingSegment > goodHypotheses;
	private List< LabelingSegment > badHypotheses;
	private List< LabelingSegment > predictionSet;
	private int maxHypothesisSize = 1000; // gets set to more sensible value in constructor
	private int minHypothesisSize = 16;
	private MetaSegRandomForestClassifier rf;
	private int displaySegmentCount;
	private List< LabelingSegment > trainSetForThisIter;
	private List< Integer > trainSetTimeForThisIter;
	public String alMode;
	private String lastClassifiedSegmentClass;
	private int undoSteps; //Only implementing 1 step undo otherwise it'll need remembering if all the hypotheses added belong to which of bad/good hypotheses bucket


	public MetaSegCostPredictionTrainerModel( final MetaSegModel metaSegModel ) {
		parentModel = metaSegModel;
		costs = new HashMap<>();

	}

	public MetaSegModel getParentModel() {
		return parentModel;
	}

	public LabelingFrames getLabelings() {
		if ( this.labelingFrames == null ) {
			System.out.println( "Entered!" );
			labelingFrames = new LabelingFrames( parentModel.getSegmentationModel(), 1, Integer.MAX_VALUE );
			labelingFrames.setMaxSegmentSize( maxHypothesisSize );
			labelingFrames.setMinSegmentSize( minHypothesisSize );
			MetaSegLog.log.info( "...processing LabelFrame inputs..." );
			labelingFrames.processFrames();
		}

		return labelingFrames;
	}

	public void setMaxPixelComponentSize( int maxValue ) {
		maxHypothesisSize = maxValue;
	}

	public void setMinPixelComponentSize( int minValue ) {
		minHypothesisSize = minValue;
	}

	public int getMaxPixelComponentSize() {
		return maxHypothesisSize;
	}

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

	public void installBehaviour( LabelingSegment labelingSegment ) {
		registerKeyBinding( KeyStroke.getKeyStroke( KeyEvent.VK_Y, 0 ), "Yes", new AbstractAction() {

			@Override
			public void actionPerformed( ActionEvent e ) {
				goodHypotheses.add( labelingSegment );
				lastClassifiedSegmentClass = "good";
				undoSteps = 0;
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

				badHypotheses.add( labelingSegment );
				lastClassifiedSegmentClass = "bad";
				undoSteps = 0;
				modifyPredictionSet();
				MetaSegLog.log.info( "Added as bad segment!" );
				try {
					selectSegmentForDisplay();
				} catch ( Exception e1 ) {
					e1.printStackTrace();
				}
			}

		} );

		registerKeyBinding( KeyStroke.getKeyStroke( KeyEvent.VK_U, 0 ), "Undo", new AbstractAction() {

			@Override
			public void actionPerformed( ActionEvent e ) {

				undoSteps = undoSteps + 1;
				if ( undoSteps >= 2 ) {
					JOptionPane
							.showMessageDialog( null, "Only one step undo allowed ..." );
					MetaSegLog.log.info( "Cannot undo more than one step..." );
				}
				else {
					MetaSegLog.log.info( "Fetching last classified hypothesis for undo..." );
					if ( lastClassifiedSegmentClass == "good" ) {
						goodHypotheses.remove( goodHypotheses.size() - 1 );
					} else {
						badHypotheses.remove( badHypotheses.size() - 1 );
					}
					if ( displaySegmentCount > 0 ) {
						displaySegmentCount = displaySegmentCount - 2;
					} else {
						JOptionPane
								.showMessageDialog( null, "Nothing available to undo ..." );
					}
					displayNextSegment();
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

	public void randomizeSegmentHypotheses() {
		List< ValuePair< LabelingSegment, Integer > > segsWithIdAndTime = getAllSegsWithIdAndTime();
		int totalHypotheses = segsWithIdAndTime.size();
		randomizedHypothesesTimeIndices = new ArrayList< Integer >(); //Set pred set to segsWithIdAndTime. Remove this and randomize in setTrainingSetForDisplay
		randomizedAllHypotheses = new ArrayList< LabelingSegment >();
		getAllSegmentsRandomized( segsWithIdAndTime, totalHypotheses );
		predictionSet = new ArrayList<>( randomizedAllHypotheses );
		goodHypotheses = new ArrayList< LabelingSegment >();
		badHypotheses = new ArrayList< LabelingSegment >();
	}


	private List< ValuePair< LabelingSegment, Integer > > getAllSegsWithIdAndTime() {
		List< List< LabelingSegment > > allSegsAllTime = new ArrayList< List< LabelingSegment > >( labelingFrames.getSegments() );
		List< ValuePair< LabelingSegment, Integer > > segsWithIdAndTime = new ArrayList<>();

		for ( int time = 0; time < allSegsAllTime.size(); time++ ) {
			for ( LabelingSegment ls : allSegsAllTime.get( time ) ) {
				segsWithIdAndTime.add( new ValuePair< LabelingSegment, Integer >( ls, time ) );
			}
		}
		return segsWithIdAndTime;
	}

	private void getAllSegmentsRandomized( List< ValuePair< LabelingSegment, Integer > > segsWithIdAndTime, int k ) {
		for ( int dispHyp = 0; dispHyp < k; dispHyp++ ) {
			Random rand = new Random();
			int randId = rand.nextInt( segsWithIdAndTime.size() );
			randomizedAllHypotheses.add( segsWithIdAndTime.get( randId ).getA() );
			randomizedHypothesesTimeIndices.add( segsWithIdAndTime.get( randId ).getB() );
			segsWithIdAndTime.remove( randId );

		}
	}

	public void setTrainingSetForDisplay() {
		trainSetForThisIter = new ArrayList<>( randomizedAllHypotheses );
		trainSetTimeForThisIter = new ArrayList<>( randomizedHypothesesTimeIndices );
		displaySegmentCount = -1;
	}

	private void selectSegmentForDisplay() throws Exception {
		if ( goodHypotheses.size() < 1 || badHypotheses.size() < 1 ) {
			//No need to do intermediate prediction as if there is no instance of one class, everything will be predicted to other class
			displayNextSegment();
		} else {
			//Also need to check if checkbox is on
			startTrainingPhase();
			double uncertaintyLB;
			double uncertaintyUB;
			LabelingSegment chosenSeg = null;
			if ( alMode == "active learning (normal)" ) {
				uncertaintyLB = -0.8d;
				uncertaintyUB = -0.2d;
				chosenSeg = pickUncertianSegmentIteratively( uncertaintyLB, uncertaintyUB );
			} else if ( alMode == "active learning (class balance)" ) { //still to do for random mode
				if ( goodHypotheses.size() >= badHypotheses.size() ) {
					uncertaintyLB = -0.49d;
					uncertaintyUB = -0.2d;
				} else {
					uncertaintyLB = -0.8d;
					uncertaintyUB = -0.51d;
				}
				chosenSeg = pickUncertianSegmentIteratively( uncertaintyLB, uncertaintyUB );
			} else if ( alMode == "random" ) {
				List< LabelingSegment > allSegments = new ArrayList<>( predictionSet );
				if ( allSegments.isEmpty() ) {
					System.out.println( "Empty costs!" );
				}
				Random rand = new Random();
				int n = rand.nextInt( allSegments.size() );
				chosenSeg = allSegments.get( n );
			}
			displaySelectedSegment( chosenSeg );
		}
	}

	private void displaySelectedSegment( LabelingSegment chosenSeg ) { //Can be removed later or modified to accommodate displaynextSegment()
		showSeg( chosenSeg );
	}

	private void displayNextSegment() {
		displaySegmentCount = displaySegmentCount + 1;

		if ( displaySegmentCount < trainSetForThisIter.size() ) {
			showSeg( trainSetForThisIter.get( displaySegmentCount ) );
		} else {
			modifyPredictionSet(); //Is it needed anymore?
			JOptionPane.showMessageDialog( null, "Finished classifying all hypotheses..." );
		}
	}

	private void showSeg( LabelingSegment chosenSeg ) {
		bdvRemoveAll();
		bdvAdd( parentModel.getRawData(), "RAW" );
		final int c = 1;
		final RandomAccessibleInterval< IntType > hypothesisImage = DataMover.createEmptyArrayImgLike( parentModel.getRawData(), new IntType() );
		LabelingSegment segment = chosenSeg;
		Integer time = findTimeIndexOfQueriedSegemt( segment );

		IterableRegion< ? > region = segment.getRegion();

		IntervalView< IntType > retSlice =
				Views.hyperSlice(
						hypothesisImage,
						parentModel.getTimeDimensionIndex(),
						time );
		try {
			Regions.sample( region, retSlice ).forEach( t -> t.set( c ) );

		} catch ( final ArrayIndexOutOfBoundsException aiaob ) {
			MetaSegLog.log.error( aiaob );
		}
		bdvHandlePanel.getViewerPanel().setTimepoint( time );
		bdvAdd( hypothesisImage, "Classifying", 0, 7, new ARGBType( 0x00FF00 ), true );
		installBehaviour( segment );
	}


	private LabelingSegment pickUncertianSegmentIteratively( double uncertaintyLB, double uncertaintyUB ) {
		int[] potentIds = Utils.uniqueRand( ( int ) ( 0.15 * predictionSet.size() ), predictionSet.size() );
		int id = 0;
		double cost;
		LabelingSegment predHyp;
		do {
			predHyp = predictionSet.get( potentIds[ id ] );
			id = id + 1;
			List< LabelingSegment > hypothesisSet = new ArrayList<>();
			hypothesisSet.add( predHyp );
			Map< LabelingSegment, Double > segAndCost = computeIntermediateCosts( hypothesisSet );
			cost = segAndCost.get( predHyp );

		}
		while ( cost > uncertaintyLB && cost < uncertaintyUB );
		return predHyp;
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
		rf = new MetaSegRandomForestClassifier();
		rf.buildRandomForest();
		rf.initializeTrainingData( goodHypotheses, badHypotheses );
		trainForest( rf );
	}

	private void trainForest( MetaSegRandomForestClassifier rf ) throws Exception {
		rf.train();
	}

	public Map< LabelingSegment, Double > computeAllCosts() { //Maybe needs to go away
		costs = rf.predict( predictionSet );
		System.out.println( "Size of costs:" + costs.size() );
		for ( LabelingSegment segment : goodHypotheses ) {
			costs.put( segment, -1d );
		}
		for ( LabelingSegment segment : badHypotheses ) {
			costs.put( segment, 100d ); //Setting positive costs (aggressive) instead of 0 to ensure bad hypotheses are never selected by optimization
		}
		System.out.println( "Size of costs updated:" + costs.size() );
		System.out.println( "Size of labels:" + randomizedAllHypotheses.size() );

		return costs;
	}

	public Map< LabelingSegment, Double > computeIntermediateCosts( List< LabelingSegment > predHyp ) { //costs needs to be local
		Map< LabelingSegment, Double > localCosts = rf.predict( predHyp );
		System.out.println( "Size of costs:" + localCosts.size() );
		return localCosts;
	}


	@Override
	public < T extends RealType< T > & NativeType< T > > BdvSource bdvGetSourceFor( RandomAccessibleInterval< T > img ) {
		// TODO Auto-generated method stub
		return null;
	}

	public void getTrainingData() {
		setTrainingSetForDisplay();
		JOptionPane
				.showMessageDialog( null, "Starting manual classification step, press Y/N to classify as good/bad hypothesis when displayed..." );
		displayNextSegment();
	}

	private int findTimeIndexOfQueriedSegemt( LabelingSegment segment ) {
		return randomizedHypothesesTimeIndices.get( randomizedAllHypotheses.indexOf( segment ) );
	}

}
