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
import com.indago.ui.bdv.BdvWithOverlaysOwner;

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
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/**
 * @author jug
 */
public class MetaSegCostPredictionTrainerModel implements CostFactory< LabelingSegment >, BdvWithOverlaysOwner {

	private final MetaSegModel parentModel;
	private LabelingFrames labelingFrames;

	private Map< LabelingSegment, Double > costs;

	private BdvHandlePanel bdvHandlePanel;
	private final List< RandomAccessibleInterval< IntType > > imgs = new ArrayList<>();
	private final List< BdvSource > bdvSources = new ArrayList<>();
	private final List< BdvOverlay > overlays = new ArrayList<>();
	private final List< BdvSource > bdvOverlaySources = new ArrayList<>();
	private ArrayList< LabelingSegment > manualTrainHypotheses;
	private ArrayList< Integer > manualTrainHypothesesTimeIndices;
	private ArrayList< Integer > alreadyDisplayedHypotheses;
	private ArrayList< LabelingSegment > goodHypotheses;
	private ArrayList< LabelingSegment > badHypotheses;
	private int maxHypothesisSize = 1000; // gets set to more sensible value in constructor
	private int minHypothesisSize = 16;

	public MetaSegCostPredictionTrainerModel( final MetaSegModel metaSegModel ) {
		parentModel = metaSegModel;
		costs = new HashMap<>();

	}

	public LabelingFrames getLabelings() {
		if ( this.labelingFrames == null ) {
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

	@Override
	public < T extends RealType< T > & NativeType< T > > BdvSource bdvGetSourceFor( RandomAccessibleInterval< T > img ) {
		final int idx = imgs.indexOf( img );
		if ( idx == -1 ) return null;
		return bdvGetSources().get( idx );
	}

	@Override
	public List< BdvSource > bdvGetOverlaySources() {
		return this.bdvOverlaySources;
	}

	@Override
	public List< BdvOverlay > bdvGetOverlays() {
		return this.overlays;
	}

	public void installBehaviour( LabelingSegment labelingSegment ) {
		registerKeyBinding( KeyStroke.getKeyStroke( KeyEvent.VK_Y, 0 ), "Yes", new AbstractAction() {

			@Override
			public void actionPerformed( ActionEvent e ) {
				goodHypotheses.add( labelingSegment );
				MetaSegLog.log.info( "Added as good segment!" );
				showTrainSegment();
			}

		} );

		registerKeyBinding( KeyStroke.getKeyStroke( KeyEvent.VK_N, 0 ), "No", new AbstractAction() {

			@Override
			public void actionPerformed( ActionEvent e ) {
				badHypotheses.add( labelingSegment );
				MetaSegLog.log.info( "Added as bad segment!" );
				showTrainSegment();
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
		bdvRemoveAllOverlays();

		imgs.clear();
		overlays.clear();

		bdvAdd( parentModel.getRawData(), "RAW" );
		final int bdvTime = bdvHandlePanel.getViewerPanel().getState().getCurrentTimepoint();

	}

	public void getRandomlySelectedSegmentHypotheses() {
		int maxNumberOfDisplayHypotheses = 100; //TODO select this number more sensibly, maybe expose as parameter
		manualTrainHypothesesTimeIndices = new ArrayList< Integer >();
		manualTrainHypotheses = new ArrayList< LabelingSegment >();
		
		List< List< LabelingSegment > > allSegs = labelingFrames.getSegments();
		for ( int dispHyp = 0; dispHyp < maxNumberOfDisplayHypotheses; dispHyp++ ) {
			Random rand = new Random();
			int randTime = rand.nextInt( ( int ) parentModel.getNumberOfFrames() );
			List< LabelingSegment > timeSegs = allSegs.get(randTime);
			Random rand2 = new Random();
			int randSeg = rand2.nextInt( timeSegs.size() );
			manualTrainHypotheses.add( timeSegs.get( randSeg ) );
			manualTrainHypothesesTimeIndices.add( randTime );
			allSegs.get( randTime ).remove( randSeg );
		}
		
		alreadyDisplayedHypotheses = new ArrayList< Integer >( Collections.nCopies( manualTrainHypotheses.size(), 0 ) );
		goodHypotheses = new ArrayList< LabelingSegment >();
		badHypotheses = new ArrayList< LabelingSegment >();
		JOptionPane
				.showMessageDialog( null, "Starting manual classification step, press Y/N to classify as good/bad hypothesis when displayed..." );
		showTrainSegment();
	}

	public void showTrainSegment() {

		bdvRemoveAll();
		bdvRemoveAllOverlays();

		imgs.clear();
		overlays.clear();

		bdvAdd( parentModel.getRawData(), "RAW" );
		int hypothesisCount = alreadyDisplayedHypotheses.size(); //The hypotheses being displayed will never equal this
		final int c = 1;
		final RandomAccessibleInterval< IntType > hypothesisImage = DataMover.createEmptyArrayImgLike( parentModel.getRawData(), new IntType() );

		for(int iter = 0; iter< alreadyDisplayedHypotheses.size(); iter++) {
			if(alreadyDisplayedHypotheses.get( iter ) == 0) {
				hypothesisCount = iter;
				System.out.println( hypothesisCount );
				alreadyDisplayedHypotheses.set( iter, 1 );
				break;
			}
				
		}
		if ( hypothesisCount == alreadyDisplayedHypotheses.size() ) {
			JOptionPane.showMessageDialog( null, "Finished classifying all hypotheses..." );
			return;
		}
		else {
			IterableRegion< ? > region = manualTrainHypotheses.get( hypothesisCount ).getRegion();
			IntervalView< IntType > retSlice =
					Views.hyperSlice(
							hypothesisImage,
							parentModel.getTimeDimensionIndex(),
							manualTrainHypothesesTimeIndices.get( hypothesisCount ) );
			try {
				Regions.sample( region, retSlice ).forEach( t -> t.set( c ) );

			} catch ( final ArrayIndexOutOfBoundsException aiaob ) {
				MetaSegLog.log.error( aiaob );
			}
			bdvHandlePanel.getViewerPanel().setTimepoint( manualTrainHypothesesTimeIndices.get( hypothesisCount ) );
			bdvAdd( hypothesisImage, "Classifying", 0, 7, new ARGBType( 0x00FF00 ), true );
			installBehaviour( manualTrainHypotheses.get( hypothesisCount ) );
		}
		
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
}
