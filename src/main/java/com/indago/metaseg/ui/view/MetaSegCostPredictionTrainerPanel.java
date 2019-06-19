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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTextField;

import com.indago.metaseg.MetaSegLog;
import com.indago.metaseg.ui.model.MetaSegCostPredictionTrainerModel;
import com.indago.metaseg.ui.util.Utils;

import bdv.util.Bdv;
import bdv.util.BdvHandlePanel;
import net.miginfocom.swing.MigLayout;

/**
 * @author jug
 */
public class MetaSegCostPredictionTrainerPanel extends JPanel implements ActionListener, FocusListener {

	private static final long serialVersionUID = 3940247743127023839L;

	MetaSegCostPredictionTrainerModel model;

	private JSplitPane splitPane;
	private JButton btnFetch;
	private JButton btnRandCosts;
	private JButton btnPrepareTrainData;

	private JTextField txtMaxPixelComponentSize;

	private JTextField txtMinPixelComponentSize;

	private JButton btnStartTrain;

	private JButton btnPredCosts;

	private JButton btnContinueActiveLearning;

	private ButtonGroup trainingModeButtons;

	private JSlider transparencySlider;

	public MetaSegCostPredictionTrainerPanel( final MetaSegCostPredictionTrainerModel costTrainerModel ) {
		super( new BorderLayout() );
		this.model = costTrainerModel;
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

		final JPanel panelPrepareTrainData = new JPanel( new MigLayout() );
		panelPrepareTrainData.setBorder( BorderFactory.createTitledBorder( "data prep" ) );

		btnPrepareTrainData = new JButton( "prepare training data" );
		btnPrepareTrainData.addActionListener( this );
		panelPrepareTrainData.add( btnPrepareTrainData, "growx, wrap" );

		final JPanel panelTrain = new JPanel( new MigLayout() );
		panelTrain.setBorder( BorderFactory.createTitledBorder( "training" ) );

		trainingModeButtons = new ButtonGroup();
		JRadioButton bRandom = new JRadioButton( "random" );
		JRadioButton bActiveLearningNormal = new JRadioButton( "active learning (normal)" );
		JRadioButton bActiveLeraningWithBalance = new JRadioButton( "active learning (class balance)" );

		trainingModeButtons.add( bRandom );
		trainingModeButtons.add( bActiveLearningNormal );
		trainingModeButtons.add( bActiveLeraningWithBalance );

		btnStartTrain = new JButton( "train" );
		btnStartTrain.addActionListener( this );
		btnContinueActiveLearning = new JButton( "continue learning" );
		btnContinueActiveLearning.addActionListener( this );

		panelTrain.add( bRandom, "span 2, growx, wrap" );
		panelTrain.add( bActiveLearningNormal, "span 2, growx, wrap" );
		panelTrain.add( bActiveLeraningWithBalance, "span 2, gapbottom 15, growx, wrap" );
		panelTrain.add( btnStartTrain, "growx, wrap" );
		panelTrain.add( btnContinueActiveLearning, "growx, wrap" );

		final JPanel panelCostPrediction = new JPanel( new MigLayout() );
		panelCostPrediction.setBorder( BorderFactory.createTitledBorder( "cost prediction" ) );

		btnRandCosts = new JButton( "set random costs" );
		btnRandCosts.addActionListener( this );

		btnPredCosts = new JButton( "set predicted costs" );
		btnPredCosts.addActionListener( this );

		panelCostPrediction.add( btnPredCosts, "growx, wrap" );
		panelCostPrediction.add( btnRandCosts, "growx, wrap" );

		controls.add( panelFetch, "growx, wrap" );
		controls.add( panelPrepareTrainData, "growx, wrap" );
		controls.add( panelTrain, "growx, wrap" );
		controls.add( panelCostPrediction, "growx, wrap" );

		bActiveLeraningWithBalance.doClick();

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
		} else
		if (e.getSource().equals( btnRandCosts )) {
			actionSetRandomCosts();
		} else if ( e.getSource().equals( btnPrepareTrainData ) ) {
			actionFetchForManualClassify();
		} else if ( e.getSource().equals( btnStartTrain ) ) {
			try {
				actionStartTrain();
			} catch ( Exception e1 ) {
				e1.printStackTrace();
			}
		} else if ( e.getSource().equals( btnPredCosts ) ) {
			actionSetPredCosts();
		} else if ( e.getSource().equals( btnContinueActiveLearning ) ) {
			actionContinueActiveLearning();
		}
	}

	private void actionSetPredCosts() {
		MetaSegLog.log.info( "Setting predicted cost values..." );
		model.setPredictedCosts();

	}

	private void actionStartTrain() throws Exception {
		MetaSegLog.log.info( "Starting feature extraction and classifer training..." );
		model.startTrainingPhase();
	}

	private void actionFetchForManualClassify() {
		MetaSegLog.log.info( "Fetching random segments for manual classification..." );
		model.setQuit( false );
		model.randomizeSegmentHypotheses();
		model.getTrainingData();
	}

	private void actionFetch() {

//		parseAndSetParametersInModel();
		model.getLabelings();
		model.getConflictGraphs();
		model.getConflictCliques();
		MetaSegLog.log.info( "Segmentation results fetched!" );
	}

	private void actionSetRandomCosts() {
		MetaSegLog.log.info( "Setting random cost values." );
		model.setRandomSegmentCosts();
	}

	private void actionContinueActiveLearning() {
		MetaSegLog.segmenterLog.info( "In active learning mode..." );
		model.setIterateActiveLearningLoop( true );
		model.setQuit( false );
		if ( getTrainingMode() == "random" ) {
			model.setALMode( "random" );
		} else if ( getTrainingMode() == "active learning (normal)" ) {
			model.setALMode( "active learning (normal)" );;
		} else if ( getTrainingMode() == "active learning (class balance)" ) {
			model.setALMode( "active learning (class balance)" );
		}
		model.getTrainingData();
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

	public String getTrainingMode() {
		return Utils.getSelectedButtonText( trainingModeButtons );
	}

}
