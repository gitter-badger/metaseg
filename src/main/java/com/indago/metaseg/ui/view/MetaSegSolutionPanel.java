/**
 *
 */
package com.indago.metaseg.ui.view;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

import org.scijava.Context;

import com.indago.fg.Assignment;
import com.indago.metaseg.MetaSegLog;
import com.indago.metaseg.ui.model.MetaSegSolverModel;
import com.indago.metaseg.ui.util.SolutionExporter;
import com.indago.pg.IndicatorNode;
import com.indago.pg.segments.SegmentNode;
import com.indago.ui.util.UniversalFileChooser;

import bdv.util.Bdv;
import bdv.util.BdvHandlePanel;
import net.imagej.ops.OpMatchingService;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.roi.IterableRegion;
import net.imglib2.roi.geom.real.Polygon2D;
import net.miginfocom.swing.MigLayout;

/**
 * @author jug
 */
public class MetaSegSolutionPanel extends JPanel implements ActionListener {

	private static final long serialVersionUID = -2148493794258482336L;

	private final MetaSegSolverModel model;

	private JSplitPane splitPane;
	private JButton btnContinueMetatrain;

	private JButton btnExport;
	OpService ops = new Context( OpService.class, OpMatchingService.class ).getService( OpService.class );

	public MetaSegSolutionPanel( final MetaSegSolverModel solutionModel ) {
		super( new BorderLayout() );
		this.model = solutionModel;
		buildGui();
	}

	private void buildGui() {
		final JPanel viewer = new JPanel( new BorderLayout() );
		model.bdvSetHandlePanel(
				new BdvHandlePanel( ( Frame ) this.getTopLevelAncestor(), Bdv
						.options()
						.is2D() ) );
		viewer.add( model.bdvGetHandlePanel().getViewerPanel(), BorderLayout.CENTER );
		model.bdvGetHandlePanel().getViewerPanel().getDisplay().addMouseListener( new MouseAdapter() {

			@Override
			public void mouseMoved( MouseEvent e ) {
				RealPoint p = new RealPoint( 3 );
				model.bdvGetHandlePanel().getViewerPanel().getGlobalMouseCoordinates( p );
				int time = model.bdvGetHandlePanel().getViewerPanel().getState().getCurrentTimepoint();
				Assignment< IndicatorNode > solution = model.getPgSolution( time );
				ArrayList< SegmentNode > chosenSegs = new ArrayList<>();
				for ( final SegmentNode segVar : model.getProblems().get( time ).getSegments() ) {
					if ( solution.getAssignment( segVar ) == 1 ) {
						chosenSegs.add( segVar );
					}
				}
				for ( SegmentNode segmentNode : chosenSegs ) {
					IterableRegion< ? > region = segmentNode.getSegment().getRegion();
					Polygon2D poly = ops.geom().contour( ( RandomAccessibleInterval ) region, true );
					List< RealLocalizable > vertices = poly.vertices();
					int[] xpoints = new int[ vertices.size() ];
					int[] ypoints = new int[ vertices.size() ];
					for ( int i = 0; i < vertices.size(); i++ ) {
						xpoints[ i ] = ( int ) vertices.get( i ).getFloatPosition( 0 );
						ypoints[ i ] = ( int ) vertices.get( i ).getFloatPosition( 1 );
					}
					Polygon jPoly = new Polygon( xpoints, ypoints, xpoints.length );
					if ( jPoly.contains( new Point( ( int ) p.getDoublePosition( 0 ), ( int ) p.getDoublePosition( 1 ) ) ) ) {
						System.out.println( "centroid:" + ops.geom().centroid( poly ) );
					}
				}

			}

		} );
		model.populateBdv();

		final MigLayout layout = new MigLayout( "", "[][grow]", "" );
		final JPanel controls = new JPanel( layout );

		final JPanel panelExport = new JPanel( new MigLayout() );
		panelExport.setBorder( BorderFactory.createTitledBorder( "export" ) );

		final JPanel panelContinueMetaTrain = new JPanel( new MigLayout() );
		panelContinueMetaTrain.setBorder( BorderFactory.createTitledBorder( "" ) );
		btnContinueMetatrain = new JButton( "continue meta training" );
		btnContinueMetatrain.addActionListener( this );
		panelContinueMetaTrain.add( btnContinueMetatrain, "growx, wrap" );


		btnExport = new JButton( "export SEG images" );
		btnExport.addActionListener( this );
		panelExport.add( btnExport, "growx, wrap" );

		controls.add( panelContinueMetaTrain, "growx, wrap" );
		controls.add( panelExport, "growx, wrap" );

		final JSplitPane splitPane = new JSplitPane( JSplitPane.HORIZONTAL_SPLIT, controls, viewer );
		splitPane.setResizeWeight( 0.1 ); // 1.0 == extra space given to left component alone!
		this.add( splitPane, BorderLayout.CENTER );
	}

	/**
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	@Override
	public void actionPerformed( final ActionEvent e ) {
		if ( e.getSource().equals( btnContinueMetatrain ) ) {
			try {
				actionContinueMetaTrain();
			} catch ( Exception e1 ) {
				e1.printStackTrace();
			}
		} else if ( e.getSource().equals( btnExport ) ) {
			actionExportCurrentSolution();
		}
	}


	private void actionExportCurrentSolution() {
		MetaSegLog.segmenterLog.info( "Exporting SEG compatible images..." );
		final File projectFolderBasePath = UniversalFileChooser.showLoadFolderChooser(
				model.bdvGetHandlePanel().getViewerPanel(),
				"",
				"Choose folder for SEG format images export..." );
		if ( projectFolderBasePath.exists() && projectFolderBasePath.isDirectory() ) {
			SegImagesExport( projectFolderBasePath );
		} else {
			JOptionPane.showMessageDialog( this, "Please choose a valid folder for this export!", "Selection Error", JOptionPane.ERROR_MESSAGE );
		}
		MetaSegLog.segmenterLog.info( "Done!" );
	}

	private void SegImagesExport( File projectFolderBasePath ) {
		SolutionExporter.exportSegData( model, projectFolderBasePath );

	}

	private void actionContinueMetaTrain() throws Exception {
		MetaSegLog.segmenterLog.info( "Starting MetaSeg optimization..." );
		model.getModel().getMainPanel().getTabs().setSelectedComponent( model.getModel().getMainPanel().getTabTraining() );
		model.getModel().getCostTrainerModel().setTrainingSetForDisplay();
		model.getModel().getCostTrainerModel().selectSegmentForDisplay();

	}
}
