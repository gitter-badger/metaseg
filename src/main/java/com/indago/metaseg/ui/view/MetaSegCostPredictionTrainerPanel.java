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

	private JSplitPane splitPane;
	private JButton btnFetch;
	private JButton btnRandCosts;
	private JButton btnPrepareTrainData;

	private JButton btnManClassification;

	private JTextField txtMaxPixelComponentSize;

	private JTextField txtMinPixelComponentSize;

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
		txtMaxPixelComponentSize.addActionListener( this );
		txtMaxPixelComponentSize.addFocusListener( this );
		txtMinPixelComponentSize = new JTextField( 5 );
		txtMinPixelComponentSize.addActionListener( this );
		txtMinPixelComponentSize.addFocusListener( this );

		btnFetch = new JButton( "fetch segments" );
		btnFetch.addActionListener( this );

		panelFetch.add( new JLabel( "Max segment size:" ), "growx" );
		panelFetch.add( txtMaxPixelComponentSize, "growx, wrap" );
		panelFetch.add( new JLabel( "Min segment size:" ), "growx" );
		panelFetch.add( txtMinPixelComponentSize, "growx, wrap" );
		panelFetch.add( btnFetch, "growx, wrap" );

		final JPanel panelManClassify = new JPanel( new MigLayout() );
		panelManClassify.setBorder( BorderFactory.createTitledBorder( "train data creation" ) );
		btnPrepareTrainData = new JButton( "prepare training data" );
		btnPrepareTrainData.addActionListener( this );
		btnManClassification = new JButton( "show for classification" );
		btnManClassification.addActionListener( this );

		panelManClassify.add( btnPrepareTrainData, "growx, wrap" );
		panelManClassify.add( btnManClassification, "growx, wrap" );

		final JPanel panelTraining = new JPanel( new MigLayout() );
		panelTraining.setBorder( BorderFactory.createTitledBorder( "training" ) );

		btnRandCosts = new JButton( "set random costs" );
		btnRandCosts.addActionListener( this );

		panelTraining.add( btnRandCosts, "growx, wrap" );

		controls.add( panelFetch, "growx, wrap" );
		controls.add( panelManClassify, "growx, wrap" );
		controls.add( panelTraining, "growx, wrap" );

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
			actionFetchTrain();
		} else if ( e.getSource().equals( btnManClassification ) ) {
			actionShowTrainSegment();
		}
	}

	private void actionShowTrainSegment() {
		MetaSegLog.log.info( "Running manual classification routine..." );
		model.showTrainSegment();
	}

	private void actionFetchTrain() {
		MetaSegLog.log.info( "Fetching random segments for manual classification..." );
		model.getRandomlySelectedSegmentHypotheses();
	}

	private void actionFetch() {
		MetaSegLog.log.info( "Fetching segmentation results..." );
		parseAndSetParametersInModel();
		model.getLabelings();
		model.getConflictGraphs();
		model.getConflictCliques();
		MetaSegLog.log.info( "Segmentation results fetched!" );
	}

	private void actionSetRandomCosts() {
		MetaSegLog.log.info( "Setting random cost values." );
		model.setRandomSegmentCosts();
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
