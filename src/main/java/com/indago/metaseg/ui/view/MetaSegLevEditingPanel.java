package com.indago.metaseg.ui.view;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

import com.indago.data.segmentation.LabelData;
import com.indago.data.segmentation.LabelingSegment;
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
import net.imglib2.RealRandomAccess;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.roi.IterableRegion;
import net.imglib2.roi.Regions;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelingType;
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

	private class MouseOver implements MouseListener, KeyListener {

		private RealPoint mousePointer;
		private ArrayList< SegmentNode > chosenSegsInSolution;
		private int selectedIndex;
		private List< LabelingSegment > segmentsUnderMouse;

		private void showFirstSegmentUnderMouse( int time, LabelingSegment labelingSegment ) {
			displayInEditMode( labelingSegment, time );
		}

		private void displayInEditMode( LabelingSegment labelingSegment, int time ) {
			populateBdv();
			final RandomAccessibleInterval< IntType > ret =
					DataMover.createEmptyArrayImgLike( model.getRawData(), new IntType() );
			final IntervalView< IntType > imgSolution = Views.hyperSlice( ret, model.getModel().getTimeDimensionIndex(), time );
			final IterableRegion< ? > region = labelingSegment.getRegion();
			final int c = 1;
			try {
				Regions.sample( region, imgSolution ).forEach( pixel -> pixel.set( c ) );
			} catch ( final ArrayIndexOutOfBoundsException aiaob ) {
				MetaSegLog.log.error( aiaob );
			}
			bdvAdd( ret, "lev. edit", 0, 2, new ARGBType( 0x00BFFF ), true );
			bdvHandlePanel.getViewerPanel().setTimepoint( time );
		}

		@Override
		public void mouseClicked( MouseEvent e ) {
			if ( !model.getPgSolutions().isEmpty() && !( model.getPgSolutions() == null ) ) {
				int time = bdvHandlePanel.getViewerPanel().getState().getCurrentTimepoint();
				chosenSegsInSolution = new ArrayList<>();
				Assignment< IndicatorNode > solution = model.getPgSolution( time );
				for ( final SegmentNode segVar : model.getProblems().get( time ).getSegments() ) {
					if ( solution.getAssignment( segVar ) == 1 ) {
						chosenSegsInSolution.add( segVar );
					}
				}
				ImgLabeling< LabelData, IntType > labelingFrames =
						model.getModel().getCostTrainerModel().getLabelings().getLabelingPlusForFrame( time ).getLabeling();
				mousePointer = new RealPoint( 3 );
				bdvHandlePanel.getViewerPanel().getGlobalMouseCoordinates( mousePointer );
				segmentsUnderMouse = findSegments( labelingFrames, mousePointer );
				if ( !( segmentsUnderMouse.isEmpty() ) ) {
					selectedIndex = 0;
					showFirstSegmentUnderMouse( time, segmentsUnderMouse.get( selectedIndex ) );
				}
				JComponent component = ( JComponent ) e.getSource();
				component.setToolTipText( "Number of conflicting segments for selected: " + segmentsUnderMouse.size() ); //TODO doesn't work anymore
			}
		}

		private List< LabelingSegment > findSegments( ImgLabeling< LabelData, IntType > labelingFrames, RealPoint mousePointer ) {
			List< LabelingSegment > segmentsUnderMouse = new ArrayList<>();
			final RealRandomAccess< LabelingType< LabelData > > a =
									Views.interpolate(
											Views.extendValue(
													labelingFrames,
													labelingFrames.firstElement().createVariable() ),
									new NearestNeighborInterpolatorFactory<>() )
							.realRandomAccess();

			a.setPosition( new int[] { ( int ) mousePointer.getFloatPosition( 0 ), ( int ) mousePointer.getFloatPosition( 1 ) } );
			//Only finds conflicts at mouse position
			for ( LabelData labelData : a.get() ) {
				segmentsUnderMouse.add( labelData.getSegment() );
			}
			return segmentsUnderMouse;
		}

		@Override
		public void mousePressed( MouseEvent e ) {}

		@Override
		public void mouseReleased( MouseEvent e ) {}

		@Override
		public void mouseEntered( MouseEvent e ) {}

		@Override
		public void mouseExited( MouseEvent e ) {}

		@Override
		public void keyTyped( KeyEvent e ) {}

		@Override
		public void keyPressed( KeyEvent e ) {
			if ( e.getKeyCode() == KeyEvent.VK_UP ) {
				if ( segmentsUnderMouse.size() > 1 ) {
					if ( selectedIndex == segmentsUnderMouse.size() - 1 ) {
						setSelectedIndex( 0 );
					} else {
						setSelectedIndex( selectedIndex + 1 );
					}
					displayInEditMode(
							segmentsUnderMouse.get( selectedIndex ),
							bdvHandlePanel.getViewerPanel().getState().getCurrentTimepoint() );
				}

			}
		}

		private void setSelectedIndex( int i ) {
			selectedIndex = i;
		}


		@Override
		public void keyReleased( KeyEvent e ) {
			// TODO Auto-generated method stub

		}

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
