/**
 *
 */
package com.indago.metaseg.ui.view;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

import com.indago.metaseg.MetaSegLog;
import com.indago.metaseg.ui.model.MetaSegSolverModel;
import com.indago.metaseg.ui.util.SolutionExporter;
import com.indago.ui.util.UniversalFileChooser;

import bdv.util.Bdv;
import bdv.util.BdvHandlePanel;
import net.miginfocom.swing.MigLayout;

/**
 * @author jug
 */
public class MetaSegSolutionPanel extends JPanel implements ActionListener {

	private static final long serialVersionUID = -2148493794258482336L;

	private final MetaSegSolverModel model;

	private JSplitPane splitPane;
	private JButton btnRun;

	private JButton btnExport;

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
		model.populateBdv();

		final MigLayout layout = new MigLayout( "", "[][grow]", "" );
		final JPanel controls = new JPanel( layout );

		final JPanel panelSolve = new JPanel( new MigLayout() );
		panelSolve.setBorder( BorderFactory.createTitledBorder( "solver" ) );
		btnRun = new JButton( "run" );
		btnRun.addActionListener( this );
		panelSolve.add( btnRun, "growx, wrap" );

		final JPanel panelExport = new JPanel( new MigLayout() );
		panelExport.setBorder( BorderFactory.createTitledBorder( "export" ) );

		btnExport = new JButton( "export SEG images" );
		btnExport.addActionListener( this );
		panelExport.add( btnExport, "growx, wrap" );

		controls.add( panelSolve, "growx, wrap" );
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
		if ( e.getSource().equals( btnRun ) ) {
			actionRun();
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

	private void actionRun() {
		MetaSegLog.segmenterLog.info( "Starting MetaSeg optimization..." );
		model.run();
		MetaSegLog.segmenterLog.info( "Done!" );

		model.populateBdv();
	}
}
