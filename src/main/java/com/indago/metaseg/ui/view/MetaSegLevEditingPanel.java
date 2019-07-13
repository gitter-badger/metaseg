package com.indago.metaseg.ui.view;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

import com.indago.fg.Assignment;
import com.indago.io.DataMover;
import com.indago.metaseg.MetaSegLog;
import com.indago.metaseg.ui.model.MetaSegSolverModel;
import com.indago.metaseg.ui.util.SolutionVisualizer;
import com.indago.pg.IndicatorNode;
import com.indago.pg.segments.SegmentNode;
import com.indago.ui.bdv.BdvWithOverlaysOwner;

import bdv.util.Bdv;
import bdv.util.BdvHandlePanel;
import bdv.util.BdvOverlay;
import bdv.util.BdvSource;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.roi.IterableRegion;
import net.imglib2.roi.Regions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.miginfocom.swing.MigLayout;

public class MetaSegLevEditingPanel extends JPanel implements ActionListener, BdvWithOverlaysOwner {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2148493794258482330L;
	private final MetaSegSolverModel model;
	private JButton btnForceSelect;
	private JButton btnForceRemove;
	private BdvHandlePanel bdvHandlePanel;

	private List< RealPoint > neighbors;
	private RealPoint mousePointer;
	private ArrayList< SegmentNode > chosenSegsInSolution;
	private List< BdvSource > bdvSources = new ArrayList<>();
	private List< BdvSource > bdvOverlaySources = new ArrayList<>();
	private List< BdvOverlay > overlays = new ArrayList<>();

	public MetaSegLevEditingPanel( MetaSegSolverModel solutionModel ) {
		super( new BorderLayout() );
		this.model = solutionModel;
		buildGui();
		bdvHandlePanel.getViewerPanel().getDisplay().addHandler( new MouseOver() );
	}

	private void buildGui() {
		final JPanel viewer = new JPanel( new BorderLayout() );

		if ( model.getModel().is2D() ) {
			bdvHandlePanel = new BdvHandlePanel( ( Frame ) this.getTopLevelAncestor(), Bdv
					.options()
					.is2D()
					.inputTriggerConfig( model.getModel().getDefaultInputTriggerConfig() ) );
		} else {
			bdvHandlePanel = new BdvHandlePanel( ( Frame ) this.getTopLevelAncestor(), Bdv
					.options() );
		}
		//This gives 2D/3D bdv panel for leveraged editing
		bdvAdd( model.getRawData(), "RAW" );
		viewer.add( bdvHandlePanel.getViewerPanel(), BorderLayout.CENTER );

		final MigLayout layout = new MigLayout( "", "[][grow]", "" );
		final JPanel controls = new JPanel( layout );

		final JPanel panelEdit = new JPanel( new MigLayout() );
		panelEdit.setBorder( BorderFactory.createTitledBorder( "leveraged editing" ) );

		btnForceSelect = new JButton( "force select" );
		btnForceSelect.addActionListener( this );
		btnForceRemove = new JButton( "force remove" );
		btnForceRemove.addActionListener( this );
		panelEdit.add( btnForceSelect, "growx, wrap" );
		panelEdit.add( btnForceRemove, "growx, wrap" );

		controls.add( panelEdit, "growx, wrap" );

		final JSplitPane splitPane = new JSplitPane( JSplitPane.HORIZONTAL_SPLIT, controls, viewer );
		splitPane.setResizeWeight( 0.1 ); // 1.0 == extra space given to left component alone!
		this.add( splitPane, BorderLayout.CENTER );

	}

	@Override
	public void actionPerformed( ActionEvent e ) {
		if ( e.getSource().equals( btnForceSelect ) ) {
	}else if (e.getSource().equals( btnForceRemove )) {
		
		}

	}

	public void estimateMouseContainingSegment( int time ) {
		for ( int neighbor = 0; neighbor < Math.min( 1, neighbors.size() ); neighbor++ ) {
			RealPoint candidateCentroid = neighbors.get( neighbor );
			for ( SegmentNode node : chosenSegsInSolution ) {
				if ( candidateCentroid.getDoublePosition( 0 ) == node.getSegment().getCenterOfMass().getDoublePosition( 0 ) ) {
					displayInEditMode( node, time );
				}
			}
		}

	}

	private void displayInEditMode( SegmentNode node, int time ) {
		populateBdv();
		final RandomAccessibleInterval< IntType > ret =
				DataMover.createEmptyArrayImgLike( model.getRawData(), new IntType() );
		final IntervalView< IntType > imgSolution = Views.hyperSlice( ret, model.getModel().getTimeDimensionIndex(), time );
		final IterableRegion< ? > region = node.getSegment().getRegion();
		final int c = 1;
		try {
			Regions.sample( region, imgSolution ).forEach( pixel -> pixel.set( c ) );
		} catch ( final ArrayIndexOutOfBoundsException aiaob ) {
			MetaSegLog.log.error( aiaob );
		}

		bdvAdd( ret, "lev. edit", 0, 2, new ARGBType( 0xFFFF00 ), true );
	}

	private void populateBdv() {
		bdvRemoveAll();
		bdvRemoveAllOverlays();
		overlays.clear();

		bdvAdd( model.getRawData(), "RAW" );

		final int bdvTime = bdvHandlePanel.getViewerPanel().getState().getCurrentTimepoint();
		if ( model.getPgSolutions() != null && model.getPgSolutions().size() > bdvTime && model.getPgSolutions().get( bdvTime ) != null ) {
			final RandomAccessibleInterval< IntType > imgSolution = SolutionVisualizer.drawSolutionSegmentImages( this.model );
			bdvAdd( imgSolution, "solution", 0, 2, new ARGBType( 0x00FF00 ), true );
		}

//		bdvAdd( new MetaSegSolutionOverlay( this.model ), "overlay" );

	}

	private class MouseOver implements MouseListener {

		private List< RealPoint > getSortedNeighbors( int time ) {
			mousePointer = new RealPoint( 3 );
			bdvHandlePanel.getViewerPanel().getGlobalMouseCoordinates( mousePointer );
			Assignment< IndicatorNode > solution = model.getPgSolution( time );
			chosenSegsInSolution = new ArrayList<>();
			List< RealPoint > centroids = new ArrayList<>();
			for ( final SegmentNode segVar : model.getProblems().get( time ).getSegments() ) {
				if ( solution.getAssignment( segVar ) == 1 ) {
					chosenSegsInSolution.add( segVar );
					centroids.add( new RealPoint( segVar.getSegment().getCenterOfMass() ) );
				}
			}
			Collections.sort( centroids, createComparator( mousePointer ) );
			return centroids;
		}

		private final Comparator< RealPoint > createComparator( RealPoint p ) {
			final RealPoint finalP = p;
			return new Comparator< RealPoint >() {

				@Override
				public int compare( RealPoint p0, RealPoint p1 ) {
					double ds0 = Math.pow( ( p0.getDoublePosition( 0 ) - finalP.getDoublePosition( 0 ) ), 2 ) + Math.pow(
							( p0.getDoublePosition( 0 ) - finalP
									.getDoublePosition( 0 ) ),
							2 );
					double ds1 = Math.pow( ( p1.getDoublePosition( 0 ) - finalP.getDoublePosition( 0 ) ), 2 ) + Math.pow(
							( p1.getDoublePosition( 0 ) - finalP
									.getDoublePosition( 0 ) ),
							2 );
					return Double.compare( ds0, ds1 );
				}
			};
		}

		@Override
		public void mouseClicked( MouseEvent e ) {
			if ( !model.getPgSolutions().isEmpty() && !( model.getPgSolutions() == null ) ) {
				int time = bdvHandlePanel.getViewerPanel().getState().getCurrentTimepoint();
				neighbors = getSortedNeighbors( time );
				System.out.println( "Nearest neighbor centroid is:" + neighbors.get( 0 ) );
				estimateMouseContainingSegment( time );
			}
		}

		@Override
		public void mousePressed( MouseEvent e ) {}

		@Override
		public void mouseReleased( MouseEvent e ) {}

		@Override
		public void mouseEntered( MouseEvent e ) {}

		@Override
		public void mouseExited( MouseEvent e ) {}

	}

	@Override
	public BdvHandlePanel bdvGetHandlePanel() {
		return bdvHandlePanel;
	}

	@Override
	public void bdvSetHandlePanel( final BdvHandlePanel bdvHandlePanel ) {
		this.bdvHandlePanel = bdvHandlePanel;
	}

	@Override
	public List< BdvSource > bdvGetSources() {
		return this.bdvSources;
	}

	@Override
	public < T extends RealType< T > & NativeType< T > > BdvSource bdvGetSourceFor( RandomAccessibleInterval< T > img ) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List< BdvSource > bdvGetOverlaySources() {
		return this.bdvOverlaySources;
	}

	@Override
	public List< BdvOverlay > bdvGetOverlays() {
		return this.overlays;
	}
}
