/**
 *
 */
package com.indago.metaseg.ui.view;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSplitPane;
import javax.swing.JTextField;

import com.indago.io.ProjectFile;
import com.indago.io.ProjectFolder;
import com.indago.metaseg.MetaSegLog;
import com.indago.metaseg.data.LabelingFrames;
import com.indago.metaseg.io.projectfolder.MetasegProjectFolder;
import com.indago.metaseg.ui.model.MetaSegCostPredictionTrainerModel;

import bdv.util.Bdv;
import bdv.util.BdvHandlePanel;
import indago.ui.progress.ProgressListener;
import net.miginfocom.swing.MigLayout;

/**
 * @author jug
 */
public class MetaSegCostPredictionTrainerPanel extends JPanel implements ActionListener, FocusListener {

	private static final long serialVersionUID = 3940247743127023839L;

	MetaSegCostPredictionTrainerModel model;

	private JButton btnFetch;
	private JButton btnPrepareTrainData;
	private JButton btnComputeSoln;
	private JButton btnUndo;
	private ButtonGroup trainingModeButtons;

	private JCheckBox boxContinuousRetrain;
	private JTextField txtMaxPixelComponentSize;
	private JTextField txtMinPixelComponentSize;
	private final String FOLDER_LABELING_FRAMES = "labeling_frames";
	private final String FILENAME_PGRAPH = "metaeg_problem.pg";

	private final ProjectFolder dataFolder;
	private LabelingFrames labelingFrames;
	private final List< ProgressListener > progressListeners = new ArrayList<>();
	private ProjectFolder hypothesesFolder;

	public MetaSegCostPredictionTrainerPanel( final MetaSegCostPredictionTrainerModel costTrainerModel ) {
		super( new BorderLayout() );
		this.model = costTrainerModel;
		dataFolder = model.getParentModel().getProjectFolder().getFolder( MetasegProjectFolder.LABELING_FRAMES_FOLDER );
		dataFolder.mkdirs();
		buildGui();
		loadStoredLabelingFrames(); //TODO needs implementation for loading problem graph
	}

	private void buildGui() {
		final JPanel viewer = new JPanel( new BorderLayout() );
		if ( model.getParentModel().is2D() ) {
			model.bdvSetHandlePanel(
					new BdvHandlePanel( ( Frame ) this.getTopLevelAncestor(), Bdv
							.options()
							.is2D() ) );
		} else {
			model.bdvSetHandlePanel(
					new BdvHandlePanel( ( Frame ) this.getTopLevelAncestor(), Bdv
							.options() ) );
		} //This gives 2D/3D bdv panel for meta-training
		viewer.add( model.bdvGetHandlePanel().getViewerPanel(), BorderLayout.CENTER );
		model.populateBdv();

		final MigLayout layout = new MigLayout( "", "[][grow]", "" );
		final JPanel controls = new JPanel( layout );

		final JPanel panelFetch = new JPanel( new MigLayout() );
		panelFetch.setBorder( BorderFactory.createTitledBorder( "segmentation fetching" ) );

		txtMaxPixelComponentSize = new JTextField( 5 );
		txtMaxPixelComponentSize.setText( Integer.toString( model.getMaxPixelComponentSize() ) ); //TODO Needs changing later to image size -1
		txtMaxPixelComponentSize.addActionListener( this );
		txtMaxPixelComponentSize.addFocusListener( this );
		txtMinPixelComponentSize = new JTextField( 5 );
		txtMinPixelComponentSize.setText( Integer.toString( model.getMinPixelComponentSize() ) );
		txtMinPixelComponentSize.addActionListener( this );
		txtMinPixelComponentSize.addFocusListener( this );

		btnFetch = new JButton( "fetch segments" );
		btnFetch.addActionListener( this );

		panelFetch.add( new JLabel( "Max segment size:" ), "growx" );
		panelFetch.add( txtMaxPixelComponentSize, "growx, wrap" );
		panelFetch.add( new JLabel( "Min segment size:" ), "growx" );
		panelFetch.add( txtMinPixelComponentSize, "growx, wrap" );
		panelFetch.add( btnFetch, "growx, wrap" );

		final JPanel panelTrainMode = new JPanel( new MigLayout() );
		panelTrainMode.setBorder( BorderFactory.createTitledBorder( "training mode" ) );

		trainingModeButtons = new ButtonGroup();
		JRadioButton bRandom = new JRadioButton( "random" );
		JRadioButton bActiveLearningNormal = new JRadioButton( "active learning (normal)" );
		JRadioButton bActiveLeraningWithBalance = new JRadioButton( "active learning (class balance)" );
		bRandom.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent e ) {
				model.setALMode( "random" );
				boxContinuousRetrain.setSelected( false );
			}
		} );
		bActiveLearningNormal.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent e ) {
				model.setALMode( "active learning (normal)" );
				boxContinuousRetrain.setSelected( true );
			}
		} );
		bActiveLeraningWithBalance.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent e ) {
				model.setALMode( "active learning (class balance)" );
				boxContinuousRetrain.setSelected( true );
			}
		} );

		trainingModeButtons.add( bRandom );
		trainingModeButtons.add( bActiveLearningNormal );
		trainingModeButtons.add( bActiveLeraningWithBalance );

		boxContinuousRetrain = new JCheckBox( "continuous retrain" );
		boxContinuousRetrain.addActionListener( this );

		panelTrainMode.add( bRandom, "span 2, growx, wrap" );
		panelTrainMode.add( bActiveLearningNormal, "span 2, growx, wrap" );
		panelTrainMode.add( bActiveLeraningWithBalance, "span 2, gapbottom 15, growx, wrap" );
		panelTrainMode.add( boxContinuousRetrain, "growx, wrap" );

		final JPanel panelPrepareTrainData = new JPanel( new MigLayout() );
		panelPrepareTrainData.setBorder( BorderFactory.createTitledBorder( "active learning" ) );

		btnPrepareTrainData = new JButton( "start" );
		btnPrepareTrainData.addActionListener( this );
		panelPrepareTrainData.add( btnPrepareTrainData, "growx, wrap" );

		final JPanel panelCostPrediction = new JPanel( new MigLayout() );
		panelCostPrediction.setBorder( BorderFactory.createTitledBorder( "compute" ) );

		btnComputeSoln = new JButton( "compute solution" );
		btnComputeSoln.addActionListener( this );

		panelCostPrediction.add( btnComputeSoln, "growx, wrap" );

		final JPanel panelUndo = new JPanel( new MigLayout() );
		panelUndo.setBorder( BorderFactory.createTitledBorder( "" ) );

		btnUndo = new JButton( "undo" );
		btnUndo.addActionListener( this );

		panelUndo.add( btnUndo, "growx, wrap" );

		controls.add( panelFetch, "growx, wrap" );
		controls.add( panelTrainMode, "growx, wrap" );
		controls.add( panelPrepareTrainData, "growx, wrap" );
		controls.add( panelCostPrediction, "growx, wrap" );
		controls.add( panelUndo, "growx, wrap" );

		bActiveLeraningWithBalance.doClick();
		boxContinuousRetrain.doClick();

		final JSplitPane splitPane = new JSplitPane( JSplitPane.HORIZONTAL_SPLIT, controls, viewer );
		splitPane.setResizeWeight( 0.1 ); // 1.0 == extra space given to left component alone!
		this.add( splitPane, BorderLayout.CENTER );
	}


	/**
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	@Override
	public void actionPerformed( final ActionEvent e ) {
		if (e.getSource().equals( btnFetch )) {
			actionFetch();
		} else if ( e.getSource().equals( btnPrepareTrainData ) ) {
			actionFetchForManualClassify();
		} else if ( e.getSource().equals( btnComputeSoln ) ) {

			try {
				actionComputeAllCostsAndRunSolver();
			} catch ( Exception e1 ) {
				e1.printStackTrace();
			}


		} else if ( e.getSource().equals( boxContinuousRetrain ) ) {
			JCheckBox state = ( JCheckBox ) e.getSource();
			if ( state.isSelected() ) {
				model.setContinuousRetrainState( true );
			} else {
				model.setContinuousRetrainState( false );
			}
		} else if ( e.getSource().equals( btnUndo ) ) {
			actionCallUndo();
		}
	}

	private void actionCallUndo() {
		model.callUndo();
	}

	private void actionComputeAllCostsAndRunSolver() throws Exception {
		MetaSegLog.log.info( "Starting MetaSeg optimization..." );
		model.bdvRemoveAll();
		model.bdvAdd( model.getParentModel().getRawData(), "RAW" );
		model.startTrainingPhase();
		model.computeAllCosts();
		model.getParentModel().getSolutionModel().run();
		model.getParentModel().getMainPanel().getTabs().setSelectedComponent( model.getParentModel().getMainPanel().getTabSolution() );
		MetaSegLog.segmenterLog.info( "Done!" );
		model.getParentModel().getSolutionModel().populateBdv();
		model.getParentModel().getMainPanel().getTabLevEdit().populateBdv();
	}

	private void actionFetchForManualClassify() {
		MetaSegLog.log.info( "Fetching random segments for manual classification..." );
		model.setAllSegAndCorrespTime();
		model.randomizeSegmentsAndPrepData();
		model.showFirstSegmentForManualClassification();
	}

	private void actionFetch() {
		for ( final ProgressListener progressListener : progressListeners ) {
			progressListener.resetProgress( "Purging currently fetched segment hypotheses... (1/3)", 3 );
		}
		for ( final ProgressListener progressListener : progressListeners ) {
			progressListener.hasProgressed( "Purging currently fetched segment hypotheses... (2/3)" );
		}
		// purge segmentation data
		dataFolder.getFile( FILENAME_PGRAPH ).getFile().delete();
		try {
			dataFolder.getFolder( FOLDER_LABELING_FRAMES ).deleteContent();
		} catch ( final IOException e ) {
			if ( dataFolder.getFolder( FOLDER_LABELING_FRAMES ).exists() ) {
				MetaSegLog.log.error( "Labeling frames exist but cannot be deleted." );
			}
		}
		processSegmentationInputs();
		MetaSegLog.log.info( "Segmentation results fetched!" );
	}

	@Override
	public void focusGained( FocusEvent e ) {}

	@Override
	public void focusLost( FocusEvent e ) {
		if ( e.getSource().equals( txtMaxPixelComponentSize ) || e.getSource().equals( txtMinPixelComponentSize ) ) {
			parseAndSetParametersInModel();
//				model.saveStateToFile();
		}

	}

	private void parseAndSetParametersInModel() {
		try {
			if ( txtMaxPixelComponentSize.getText().trim().isEmpty() ) {
				model.setMaxPixelComponentSize( Integer.MAX_VALUE );
			} else {
				model.setMaxPixelComponentSize( Integer.parseInt( txtMaxPixelComponentSize.getText() ) );
			}
		} catch ( final NumberFormatException e ) {
			txtMaxPixelComponentSize.setText( "" + model.getMaxPixelComponentSize() );
		}
		try {
			if ( txtMinPixelComponentSize.getText().trim().isEmpty() ) {
				model.setMinPixelComponentSize( model.getMinPixelComponentSize() );
			} else {
				model.setMinPixelComponentSize( Integer.parseInt( txtMinPixelComponentSize.getText() ) );
			}
		} catch ( final NumberFormatException e ) {
			txtMinPixelComponentSize.setText( "" + model.getMinPixelComponentSize() );
		}

	}

	private void loadStoredLabelingFrames() {
		// Loading hypotheses labeling frames if exist in project folder
		try {
			hypothesesFolder = dataFolder.addFolder( FOLDER_LABELING_FRAMES );
			hypothesesFolder.loadFiles();
//			labelingFrames.loadFromProjectFolder( hypothesesFolder );
		} catch ( final IOException ioe ) {
			ioe.printStackTrace();
		}

		// Loading stored PGraph and solution if exist in project folder
		final ProjectFile pgFile = model.getParentModel().getProjectFolder().getFile( FILENAME_PGRAPH ); //TODO Need to implement loading of problem graphs if exists already in project folder
//		if ( pgFile.exists() ) {
//			this.tr2dTraProblem =
//					new Tr2dTrackingProblem( this, tr2dModel.getFlowModel(), appearanceCosts, moveCosts, divisionCosts, disappearanceCosts );
//			boolean success;
//			try {
//				success = tr2dTraProblem.getSerializer().loadPGraph( tr2dTraProblem, pgFile.getFile() );
//			} catch ( final IOException e1 ) {
//				success = false;
//				e1.printStackTrace();
//			}
//
//		}
	}

	private void processSegmentationInputs() {
//		parseAndSetParametersInModel();
		labelingFrames = model.getLabelings();
		model.getConflictGraphs();
		model.getConflictCliques();
		labelingFrames.saveTo( hypothesesFolder, progressListeners );
	}

}
