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
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
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

	private JButton btnFetchGoodHypotheses;
	private JButton btnFetchBadHypotheses;
	private JButton btnFetchPredictionHypotheses;
	private JButton btnComputeSoln;
	private JTextField txtMaxPixelComponentSize;
	private JTextField txtMinPixelComponentSize;

	public MetaSegCostPredictionTrainerPanel( final MetaSegCostPredictionTrainerModel costTrainerModel ) {
		super( new BorderLayout() );
		this.model = costTrainerModel;
		buildGui();
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
		txtMaxPixelComponentSize.setText( Integer.toString( model.getMaxPixelComponentSize() ) );
		txtMaxPixelComponentSize.addActionListener( this );
		txtMaxPixelComponentSize.addFocusListener( this );
		txtMinPixelComponentSize = new JTextField( 5 );
		txtMinPixelComponentSize.setText( Integer.toString( model.getMinPixelComponentSize() ) );
		txtMinPixelComponentSize.addActionListener( this );
		txtMinPixelComponentSize.addFocusListener( this );

		btnFetchGoodHypotheses = new JButton( "fetch good" );
		btnFetchGoodHypotheses.addActionListener( this );
		btnFetchBadHypotheses = new JButton( "fetch bad" );
		btnFetchBadHypotheses.addActionListener( this );
		btnFetchPredictionHypotheses = new JButton( "fetch prediction" );
		btnFetchPredictionHypotheses.addActionListener( this );

		panelFetch.add( new JLabel( "Max segment size:" ), "growx" );
		panelFetch.add( txtMaxPixelComponentSize, "growx, wrap" );
		panelFetch.add( new JLabel( "Min segment size:" ), "growx" );
		panelFetch.add( txtMinPixelComponentSize, "growx, wrap" );
		panelFetch.add( btnFetchGoodHypotheses, "growx, wrap" );
		panelFetch.add( btnFetchBadHypotheses, "growx, wrap" );
		panelFetch.add( btnFetchPredictionHypotheses, "growx, wrap" );

		final JPanel panelCostPrediction = new JPanel( new MigLayout() );
		panelCostPrediction.setBorder( BorderFactory.createTitledBorder( "compute" ) );

		btnComputeSoln = new JButton( "compute solution" );
		btnComputeSoln.addActionListener( this );

		panelCostPrediction.add( btnComputeSoln, "growx, wrap" );

		controls.add( panelFetch, "growx, wrap" );
		controls.add( panelCostPrediction, "growx, wrap" );

		final JSplitPane splitPane = new JSplitPane( JSplitPane.HORIZONTAL_SPLIT, controls, viewer );
		splitPane.setResizeWeight( 0.1 ); // 1.0 == extra space given to left component alone!
		this.add( splitPane, BorderLayout.CENTER );
	}


	/**
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	@Override
	public void actionPerformed( final ActionEvent e ) {
		if (e.getSource().equals( btnFetchGoodHypotheses )) {
			actionFetch( "good" );
		} else if ( e.getSource().equals( btnFetchBadHypotheses ) ) {
			actionFetch( "bad" );
		} else if ( e.getSource().equals( btnFetchPredictionHypotheses ) ) {
			actionFetch( "pred" );
		} else if ( e.getSource().equals( btnComputeSoln ) ) {
			try {
				actionComputeAllCostsAndRunSolver();
			} catch ( Exception e1 ) {
				e1.printStackTrace();
			}

		}
	}

	private void actionComputeAllCostsAndRunSolver() throws Exception {
		MetaSegLog.log.info( "Starting MetaSeg optimization..." );
		model.bdvRemoveAll();
		model.bdvAdd( model.getParentModel().getRawData(), "RAW" );
		if ( model.isSavedCostsLoaded() == false ) {
			model.startTrainingPhase();
			model.computeAllCosts();
		}
		model.getParentModel().getSolutionModel().run();
		model.getParentModel().getMainPanel().getTabs().setSelectedComponent( model.getParentModel().getMainPanel().getTabSolution() );
		MetaSegLog.segmenterLog.info( "Done solving!" );
		MetaSegLog.segmenterLog.info( "Populating the solution ..." );
		model.getParentModel().getMainPanel().getTabSolution().getLabelEditorBasedSolutionAndLevEditingTab().populateBdv( model.getParentModel().getSolutionModel() );
	}

	private void actionFetchForManualClassify() {
		MetaSegLog.log.info( "Fetching random segments for manual classification..." );
		if ( model.isCostsExists() ) {
			int rewriteCosts = JOptionPane.showConfirmDialog(
					null,
					"costs already exist, continue purging it and recreate based on new training?",
					"Rewrite costs",
					JOptionPane.YES_NO_OPTION );
			if ( rewriteCosts == JOptionPane.YES_OPTION ) {
				model.clearAllCosts();
				model.setSavedCostsLoaded( false );
			} else {
				model.setSavedCostsLoaded( true );
				return;
			}
		} else {

		}
		model.setAllSegAndCorrespTime();
		model.randomizeSegmentsAndPrepData();
	}

	private void actionFetch( String quality ) {

		processSegmentationInputs( quality );
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

	private void processSegmentationInputs( String quality ) {
		model.createLabelingsFromScratch( quality );
		if ( quality == "pred" ) {
			model.getConflictGraphs();
			model.getConflictCliques();
			actionFetchForManualClassify();
		} else if ( quality == "good" ) {
			model.populateGoodHypothesesList();
		} else if ( quality == "bad" ) {
			model.populateBadHypothesesList();
		}

//		model.saveLabelingFrames();
//		model.setSavedCostsLoaded( false );
	}

}
