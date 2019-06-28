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

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSplitPane;
import javax.swing.JTextField;

import com.indago.metaseg.MetaSegLog;
import com.indago.metaseg.ui.model.MetaSegCostPredictionTrainerModel;

import bdv.util.Bdv;
import bdv.util.BdvHandlePanel;
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

	public MetaSegCostPredictionTrainerPanel( final MetaSegCostPredictionTrainerModel costTrainerModel ) {
		super( new BorderLayout() );
		this.model = costTrainerModel;
		buildGui();
	}

	private void buildGui() {
		final JPanel viewer = new JPanel( new BorderLayout() );
		if ( model.getParentModel().getNumberOfSpatialDimensions() == 2 ) {
			model.bdvSetHandlePanel(
					new BdvHandlePanel( ( Frame ) this.getTopLevelAncestor(), Bdv
							.options()
							.is2D() ) );
		} else if ( model.getParentModel().getNumberOfSpatialDimensions() == 3 ) {
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
	}

	private void actionFetchForManualClassify() {
		MetaSegLog.log.info( "Fetching random segments for manual classification..." );
		model.setAllSegAndCorrespTime();
		model.randomizeSegmentsAndPrepData();
		model.getTrainingData();
	}

	private void actionFetch() {

//		parseAndSetParametersInModel();
		model.getLabelings();
		model.getConflictGraphs();
		model.getConflictCliques();
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

}
