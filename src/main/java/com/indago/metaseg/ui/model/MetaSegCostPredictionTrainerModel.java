/**
 *
 */
package com.indago.metaseg.ui.model;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.stream.Collectors;

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
	private final List< BdvSource > bdvOverlaySources = new ArrayList<>();
	private List< LabelingSegment > randomizedAllHypotheses; // Stores all hypotheses in a randomized order
	private List< Integer > randomizedHypothesesTimeIndices; // Stores all hypotheses in a randomized order
	private List< LabelingSegment > goodHypotheses;
	private List< LabelingSegment > badHypotheses;
	private List< LabelingSegment > trainingSet;
	private List< LabelingSegment > predictionSet;
	private List< LabelingSegment > uncertainSegments;
	private int maxHypothesisSize = 1000; // gets set to more sensible value in constructor
	private int minHypothesisSize = 16;
	private MetaSegRandomForestClassifier rf;
	private boolean quit;
	private boolean continueActiveLearning = false;
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

	public void setRandomSegmentCosts() {
		costs = new HashMap<>();
		final Random r = new Random();
		for ( int t = 0; t < labelingFrames.getNumFrames(); t++ ) {
			for ( final LabelingSegment segment : labelingFrames.getSegments( t ) ) {
				costs.put( segment, -r.nextDouble() );
			}
			MetaSegLog.log.info( String.format( "Random costs set for %d LabelingSegments at t=%d.", labelingFrames.getSegments( t ).size(), t ) );
		}
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
				if ( quit == false ) {
					goodHypotheses.add( labelingSegment );
					lastClassifiedSegmentClass = "good";
					undoSteps = 0;
					modifyTrainingSet( labelingSegment );
					MetaSegLog.log.info( "Added as good segment!" );
					displayNextSegment();
				}
				System.out.println( "Good hypotheses" + goodHypotheses.size() );
				System.out.println( "Bad hypotheses" + badHypotheses.size() );
			}

		} );

		registerKeyBinding( KeyStroke.getKeyStroke( KeyEvent.VK_N, 0 ), "No", new AbstractAction() {

			@Override
			public void actionPerformed( ActionEvent e ) {
				if ( quit == false ) {
					badHypotheses.add( labelingSegment );
					lastClassifiedSegmentClass = "bad";
					undoSteps = 0;
					modifyTrainingSet( labelingSegment );
					MetaSegLog.log.info( "Added as bad segment!" );
					displayNextSegment();
				}
				System.out.println( "Good hypotheses" + goodHypotheses.size() );
				System.out.println( "Bad hypotheses" + badHypotheses.size() );
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
				System.out.println( "Good hypotheses" + goodHypotheses.size() );
				System.out.println( "Bad hypotheses" + badHypotheses.size() );
			}

		} );

		registerKeyBinding( KeyStroke.getKeyStroke( KeyEvent.VK_Q, 0 ), "Quit", new AbstractAction() {

			@Override
			public void actionPerformed( ActionEvent e ) {

				modifyPredictionSet();
				undoSteps = 0;
				MetaSegLog.log.info( "Quitting classifying hypotheses..." );
				quitShowingTrainSegment();

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
//		final int bdvTime = bdvHandlePanel.getViewerPanel().getState().getCurrentTimepoint();

	}

	private void quitShowingTrainSegment() {
		bdvRemoveAll();
		bdvAdd( parentModel.getRawData(), "RAW" );
		quit = true;
		JOptionPane
				.showMessageDialog( null, "Quitting manual classification step..." );
	}

	public void randomizeSegmentHypotheses() {
		List< ValuePair< LabelingSegment, Integer > > segsWithIdAndTime = getAllSegsWithIdAndTime();
		int totalHypotheses = segsWithIdAndTime.size();
		randomizedHypothesesTimeIndices = new ArrayList< Integer >();
		randomizedAllHypotheses = new ArrayList< LabelingSegment >();
		shuffleSegmentsForTraining( segsWithIdAndTime, totalHypotheses );
		trainingSet = new ArrayList<>();
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

	private List< LabelingSegment > extractDataForActiveLearningLoop() {
		List< LabelingSegment > uncertain_segments = null;
		double uncertaintyLB; 
		double uncertaintyUB;
		if ( alMode == "random" ) {
			List< LabelingSegment > all_segments = new ArrayList<>();
			List< LabelingSegment > keys = new ArrayList( costs.keySet() );
			Collections.shuffle( keys );
			for ( LabelingSegment o : keys ) {
				// Access keys/values in a random order
				all_segments.add( o );
			}
			List< LabelingSegment > diff = all_segments
					.stream()
					.filter( e -> !goodHypotheses.contains( e ) )
					.collect( Collectors.toList() );
			uncertain_segments = diff
					.stream()
					.filter( e -> !badHypotheses.contains( e ) )
					.collect( Collectors.toList() );
			if ( uncertain_segments.isEmpty() ) {
				System.out.println( "Empty costs!" );
			}

		} else if ( alMode == "active learning (normal)" ) {
			if ( !costs.isEmpty() ) {

				uncertaintyLB = -0.8d;
				uncertaintyUB = -0.2d;
				uncertain_segments = getSegmnetsByUncertaintyProbability( uncertaintyLB, uncertaintyUB );
				MetaSegLog.log.info( "showig uncertain hypotheses." );
			} else {
				System.out.println( "Empty costs!" );
			}

		} else if ( alMode == "active learning (class balance)" ) {
			if ( !costs.isEmpty() ) {
				if ( goodHypotheses.size() >= badHypotheses.size() ) {
					uncertaintyLB = -0.49d;
					uncertaintyUB = -0.2d;
					MetaSegLog.log.info( "showig more of likely bad hypotheses." );
				} else {
					uncertaintyLB = -0.8d;
					uncertaintyUB = -0.51d;
					MetaSegLog.log.info( "showig more of likely good hypotheses." );
				}
				uncertain_segments = getSegmnetsByUncertaintyProbability( uncertaintyLB, uncertaintyUB );
			} else {
				System.out.println( "Empty costs!" );
			}

		}

		return uncertain_segments;
	}

	private List< LabelingSegment > getSegmnetsByUncertaintyProbability( double uncertaintyLB, double uncertaintyUB ) {
		List< LabelingSegment > uncertain_segments;
		uncertain_segments = costs
				.entrySet()
				.stream()
				.filter( entry -> ( entry.getValue() > uncertaintyLB && entry.getValue() < uncertaintyUB ) )
				.map( Entry::getKey )
				.collect( Collectors.toList() );
		System.out.println( uncertain_segments.size() );
		return uncertain_segments;
	}

	private void shuffleSegmentsForTraining( List< ValuePair< LabelingSegment, Integer > > segsWithIdAndTime, int totalHypotheses ) {
		getAllSegmentsRandomized( segsWithIdAndTime, totalHypotheses );
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

		if ( continueActiveLearning == false ) {
			trainSetForThisIter = new ArrayList<>( randomizedAllHypotheses );
			trainSetTimeForThisIter = new ArrayList<>( randomizedHypothesesTimeIndices );
		} else {
			trainSetForThisIter = new ArrayList<>( uncertainSegments );
			trainSetTimeForThisIter = findTimeIndicesOfUncertainSegemts();
		}
		displaySegmentCount = -1;
	}

	private void displayNextSegment() {
		displaySegmentCount = displaySegmentCount + 1;

		if ( displaySegmentCount < trainSetForThisIter.size() ) {
			bdvRemoveAll();
			bdvAdd( parentModel.getRawData(), "RAW" );
			final int c = 1;
			final RandomAccessibleInterval< IntType > hypothesisImage = DataMover.createEmptyArrayImgLike( parentModel.getRawData(), new IntType() );
			LabelingSegment segment = trainSetForThisIter.get( displaySegmentCount );
			Integer time = trainSetTimeForThisIter.get( displaySegmentCount );

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
		} else {
			modifyPredictionSet();
			JOptionPane.showMessageDialog( null, "Finished classifying all hypotheses..." );
		}

	}

	private void modifyTrainingSet( LabelingSegment segment ) {
		trainingSet.add( segment );
	}

	private void modifyPredictionSet() {
		predictionSet.removeAll( trainingSet );
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

	public void startTrainingPhase() throws Exception {
		if ( !( goodHypotheses.isEmpty() ) || !( badHypotheses.isEmpty() ) ) {
			extractFeatures();
		}

	}

	private void extractFeatures() throws Exception {

		rf = new MetaSegRandomForestClassifier();
		rf.buildRandomForest();
		rf.initializeTrainingData( goodHypotheses, badHypotheses );
		rf.train();


	}

	public void setPredictedCosts() {
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
	}


	@Override
	public < T extends RealType< T > & NativeType< T > > BdvSource bdvGetSourceFor( RandomAccessibleInterval< T > img ) {
		// TODO Auto-generated method stub
		return null;
	}

	public void setQuit( boolean b ) {
		quit = b;

	}

	public void setIterateActiveLearningLoop( boolean b ) {
		continueActiveLearning = b;
	}

	public void getTrainingData() {
		if ( continueActiveLearning ) {
			uncertainSegments = extractDataForActiveLearningLoop();
			setTrainingSetForDisplay();
			displaySegments();

		} else {
			setTrainingSetForDisplay();
			JOptionPane
					.showMessageDialog( null, "Starting manual classification step, press Y/N to classify as good/bad hypothesis when displayed..." );
			displaySegments();

		}
	}

	private void displaySegments() {
		displayNextSegment();
	}

	private List< Integer > findTimeIndicesOfUncertainSegemts() {
		List< Integer > uncertainSetTimeIndices = new ArrayList<>();
		for ( LabelingSegment segment : uncertainSegments ) {
			int ind = randomizedAllHypotheses.indexOf( segment );
			uncertainSetTimeIndices.add( randomizedHypothesesTimeIndices.get( ind ) );
		}
		return uncertainSetTimeIndices;
	}

	public void setALMode( String mode ) {
		alMode = mode;
	}


}
