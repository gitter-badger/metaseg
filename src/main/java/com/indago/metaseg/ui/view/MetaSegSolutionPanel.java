/**
 *
 */
package com.indago.metaseg.ui.view;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

import com.indago.metaseg.MetaSegLog;
import com.indago.metaseg.pg.MetaSegProblem;
import com.indago.metaseg.ui.model.MetaSegSolverModel;
import com.indago.metaseg.ui.util.SolutionAndStatsExporter;
import com.indago.pg.segments.ConflictSet;
import com.indago.pg.segments.SegmentNode;
import com.indago.ui.util.UniversalFileChooser;

import bdv.util.BdvSource;
import net.imagej.ops.OpService;
import net.miginfocom.swing.MigLayout;

/**
 * @author jug
 */
public class MetaSegSolutionPanel extends JPanel implements ActionListener {

	private static final long serialVersionUID = -2148493794258482336L;

	private final MetaSegSolverModel model;
	private JButton btnContinueMetatrain;
	private JButton btnExportSegCompatibleImages;
	private JButton btnExportLabelFusionProblem;
	
	private OpService ops() {
		return model.getContext().service( OpService.class );
	}
	private LabelViewerAndEditorPanel< ? > tabSolutionAndLevEditing;

	private JButton btnExportSegSourceStats;

	public MetaSegSolutionPanel( final MetaSegSolverModel solutionModel ) {
		super( new BorderLayout() );
		this.model = solutionModel;
		buildGui();
		getLabelEditorBasedSolutionAndLevEditingTab().getSources().forEach(
				source -> ( ( BdvSource ) source ).setDisplayRange( model.getModel().getMaxRawValue(), model.getModel().getMinRawValue() ) );

	}

	public LabelViewerAndEditorPanel< ? > getLabelEditorBasedSolutionAndLevEditingTab() {
		return tabSolutionAndLevEditing;
	}

	private void buildGui() {
		final JPanel viewer = new JPanel( new BorderLayout() );

		tabSolutionAndLevEditing = new LabelViewerAndEditorPanel( model );
		viewer.add( tabSolutionAndLevEditing, BorderLayout.CENTER );

		final MigLayout layout = new MigLayout( "", "[][grow]", "" );
		final JPanel controls = new JPanel( layout );

		final JPanel panelExport = new JPanel( new MigLayout() );
		panelExport.setBorder( BorderFactory.createTitledBorder( "export" ) );

		final JPanel panelContinueMetaTrain = new JPanel( new MigLayout() );
		panelContinueMetaTrain.setBorder( BorderFactory.createTitledBorder( "" ) );
		btnContinueMetatrain = new JButton( "continue meta training" );
		btnContinueMetatrain.addActionListener( this );
		panelContinueMetaTrain.add( btnContinueMetatrain, "growx, wrap" );

		btnExportSegCompatibleImages = new JButton( "SEG images" );
		btnExportSegCompatibleImages.addActionListener( this );
		btnExportLabelFusionProblem = new JButton( "Problem graph" );
		btnExportLabelFusionProblem.addActionListener( this );
		btnExportSegSourceStats = new JButton( "Seg source stats" );
		btnExportSegSourceStats.addActionListener( this );
		panelExport.add( btnExportSegCompatibleImages, "growx, wrap" );
		panelExport.add( btnExportLabelFusionProblem, "growx, wrap" );
		panelExport.add( btnExportSegSourceStats, "growx, wrap" );

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
		if ( e.getSource().equals( btnExportSegCompatibleImages ) ) {
			actionExportCurrentSolution();
		} else if ( e.getSource().equals( btnExportLabelFusionProblem ) ) {
			actionExportLabelFusionProblem();
		} else if ( e.getSource().equals( btnExportSegSourceStats ) ) {
			actionExportSegSourceStats();
		}
	}

	private void actionExportSegSourceStats() {
		MetaSegLog.segmenterLog.info( "Computing segmnentation source statistics for the solution..." );
		final File projectFolderBasePath = UniversalFileChooser.showLoadFolderChooser(
				tabSolutionAndLevEditing.getInterfaceHandle().getViewerPanel(),
				"",
				"Choose folder for Segmentation source statistics file export..." );
		if ( projectFolderBasePath.exists() && projectFolderBasePath.isDirectory() ) {
			segSourceStatsExport( projectFolderBasePath );
		} else {
			JOptionPane.showMessageDialog( this, "Please choose a valid folder for this export!", "Selection Error", JOptionPane.ERROR_MESSAGE );
		}
		MetaSegLog.segmenterLog.info( "Done!" );
	}

	private void segSourceStatsExport( File projectFolderBasePath ) {
		final File exportFile = new File( projectFolderBasePath, "metaseg_solution_source_statistics.ss" );
		try {
			final SimpleDateFormat sdfDate = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss" );//dd/MM/yyyy
			final Date now = new Date();
			final String strNow = sdfDate.format( now );

			final BufferedWriter problemWriter = new BufferedWriter( new FileWriter( exportFile ) );
			problemWriter.write( "# MetaSeg problem export from " + strNow + "\n" );
			List< Map< String, Integer > > segSourceStatsAllTime = SolutionAndStatsExporter.exportSegSourcesStats( model, projectFolderBasePath );
			for (int frame = 0; frame< segSourceStatsAllTime.size(); frame++) {
				Map< String, Integer > segSourceStatsPerTime = segSourceStatsAllTime.get( frame );
				problemWriter.write( String.format( "\n# t=%d\n", frame ) );
				writeSegmentSourceStatsLine( segSourceStatsPerTime, problemWriter );
			}
			problemWriter.close();
		}
		catch ( final IOException e ) {
			JOptionPane
					.showMessageDialog( this, "Cannot write in selected export folder... cancel export!", "File Error", JOptionPane.ERROR_MESSAGE );
			e.printStackTrace();
		}
	}

	private void actionExportLabelFusionProblem() {
		MetaSegLog.segmenterLog.info( "Exporting problem graphs..." );
		final File projectFolderBasePath = UniversalFileChooser.showLoadFolderChooser(
				tabSolutionAndLevEditing.getInterfaceHandle().getViewerPanel(),
				"",
				"Choose folder for problem graph export..." );
		if ( projectFolderBasePath.exists() && projectFolderBasePath.isDirectory() ) {
			problemGraphExport( projectFolderBasePath );
		} else {
			JOptionPane.showMessageDialog( this, "Please choose a valid folder for this export!", "Selection Error", JOptionPane.ERROR_MESSAGE );
		}
		MetaSegLog.segmenterLog.info( "Done!" );
	}


	private void actionExportCurrentSolution() {
		MetaSegLog.segmenterLog.info( "Exporting SEG compatible images..." );
		final File projectFolderBasePath = UniversalFileChooser.showLoadFolderChooser(
				tabSolutionAndLevEditing.getInterfaceHandle().getViewerPanel(),
				"",
				"Choose folder for SEG format images export..." );
		if ( projectFolderBasePath.exists() && projectFolderBasePath.isDirectory() ) {
			segImagesExport( projectFolderBasePath );
		} else {
			JOptionPane.showMessageDialog( this, "Please choose a valid folder for this export!", "Selection Error", JOptionPane.ERROR_MESSAGE );
		}
		MetaSegLog.segmenterLog.info( "Done!" );
	}

	private void segImagesExport( File projectFolderBasePath ) {
		SolutionAndStatsExporter.exportSegData( model, projectFolderBasePath );
	}

	private void problemGraphExport( File projectFolderBasePath ) {
		final File exportFile = new File( projectFolderBasePath, "metaseg_problem.pg" );
		try {
			final SimpleDateFormat sdfDate = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss" );//dd/MM/yyyy
			final Date now = new Date();
			final String strNow = sdfDate.format( now );

			final BufferedWriter problemWriter = new BufferedWriter( new FileWriter( exportFile ) );
			problemWriter.write( "# MetaSeg problem export from " + strNow + "\n" );
			List< MetaSegProblem > msp = model.getProblems();
			problemWriter.write( "\n# === SEGMENT HYPOTHESES =================================================\n" );
			for ( int frame = 0; frame < msp.size(); frame++ ) {
				MetaSegProblem t = msp.get( frame );
				problemWriter.write( String.format( "\n# t=%d\n", frame ) );

				// write all segment hypotheses
				for ( final SegmentNode segment : t.getSegments() ) {
					writeSegmentLine( frame, segment, problemWriter );
				}
			}

			problemWriter.write( "# === CONSTRAINTS ========================================================\n\n" );
			for ( final MetaSegProblem t : msp ) {
				for ( final ConflictSet cs : t.getConflictSets() ) {
					ArrayList< Integer > idList = new ArrayList<>();
					final Iterator< SegmentNode > it = cs.iterator();
					while ( it.hasNext() ) {
						final SegmentNode segnode = it.next();
						idList.add( segnode.getSegment().getId() );
					}
					// CONFSET <id...>
					problemWriter.write( "CONFSET " );
					boolean first = true;
					for ( final int id : idList ) {
						if ( !first ) problemWriter.write( " + " );
						problemWriter.write( String.format( "%4d ", id ) );
						first = false;
					}
					problemWriter.write( " <= 1\n" );
				}
			}
			problemWriter.close();
		} catch ( final IOException e ) {
			JOptionPane
					.showMessageDialog( this, "Cannot write in selected export folder... cancel export!", "File Error", JOptionPane.ERROR_MESSAGE );
			e.printStackTrace();
		}

	}

	private void writeSegmentLine( int frame, SegmentNode segment, BufferedWriter problemWriter ) throws IOException {
		// H <time> <id> <cost> (<com_x_pos> <com_y_pos>)
		problemWriter.write(
				String.format(
						"H %3d %4d %.16f (%.1f,%.1f)\n",
						frame,
						segment.getSegment().getId(),
						segment.getCost(),
						segment.getSegment().getCenterOfMass().getFloatPosition( 0 ),
						segment.getSegment().getCenterOfMass().getFloatPosition( 1 ) ) );

	}

	private void writeSegmentSourceStatsLine(Map< String, Integer > segSourceStatsPerTime, BufferedWriter problemWriter) throws IOException{
		for ( Map.Entry< String, Integer > val : segSourceStatsPerTime.entrySet() ) {
			// writing the occurrence of elements in the arraylist
			problemWriter.write( "Segments from source " + val.getKey() + " " + "selected" + ": " + val.getValue() + " times \n" );
		}
	}

}
