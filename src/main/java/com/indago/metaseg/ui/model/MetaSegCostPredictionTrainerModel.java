/**
 *
 */
package com.indago.metaseg.ui.model;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
public class MetaSegCostPredictionTrainerModel implements CostFactory< LabelingSegment >, BdvWithOverlaysOwner, ActionListener {

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
	private int maxHypothesisSize = 32; // gets set to more sensible value in constructor
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

	public void populateBdv() {
		bdvRemoveAll();
		bdvRemoveAllOverlays();

		imgs.clear();
		overlays.clear();

		bdvAdd( parentModel.getRawData(), "RAW" );
		final int bdvTime = bdvHandlePanel.getViewerPanel().getState().getCurrentTimepoint();

	}

	public void getRandomlySelectedSegmentHypotheses() {
		int maxNumberOfDisplayHypotheses = 100;
		manualTrainHypothesesTimeIndices = new ArrayList< Integer >();
		manualTrainHypotheses = new ArrayList< LabelingSegment >();
		
		List< List< LabelingSegment > > allSegs = labelingFrames.getSegments();
		for ( int dispHyp = 0; dispHyp < maxNumberOfDisplayHypotheses; dispHyp++ ) {
			Random rand = new Random();
			int ele = rand.nextInt( ( int ) parentModel.getNumberOfFrames() );
			List< LabelingSegment > timeSegs = allSegs.get(ele);
			Random rand2 = new Random();
			manualTrainHypotheses.add( timeSegs.get( rand2.nextInt( timeSegs.size() ) ) );
			manualTrainHypothesesTimeIndices.add(  ele );
		}
		
		alreadyDisplayedHypotheses = new ArrayList< Integer >( Collections.nCopies( manualTrainHypotheses.size(), 0 ) );
	}

	@Override
	public void actionPerformed( ActionEvent e ) {
		// TODO Auto-generated method stub

	}

	public void showTrainSegment() {

		bdvRemoveAll();
		bdvRemoveAllOverlays();

		imgs.clear();
		overlays.clear();

		bdvAdd( parentModel.getRawData(), "RAW" );
		int hypothesisCount = -1;
		final int c = 1;
		final RandomAccessibleInterval< IntType > hypothesisImage = DataMover.createEmptyArrayImgLike( parentModel.getRawData(), new IntType() );

		for(int iter = 0; iter< alreadyDisplayedHypotheses.size(); iter++) {
			if(alreadyDisplayedHypotheses.get( iter ) == 0) {
				hypothesisCount = iter;
				alreadyDisplayedHypotheses.set( iter, 1 );
				break;
			}
				
		}
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
