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

import javax.swing.JButton;
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
	private JButton btnFetchTrain;

	private JButton btnManClassification;

	private JTextField txtMaxPixelComponentSize;

	private JTextField txtMinPixelComponentSize;

	private JButton bFetch;

	public MetaSegCostPredictionTrainerPanel( final MetaSegCostPredictionTrainerModel costTrainerModel ) {
		super( new BorderLayout() );
		this.model = costTrainerModel;
		buildGui();
	}

	private void buildGui() {
		final JPanel panel = new JPanel( new BorderLayout() );

		model.bdvSetHandlePanel(
				new BdvHandlePanel( ( Frame ) this.getTopLevelAncestor(), Bdv
						.options()
						.is2D() ) );

		panel.add( model.bdvGetHandlePanel().getViewerPanel(), BorderLayout.CENTER );
		model.populateBdv();

		final MigLayout layout = new MigLayout( "", "[][grow]", "" );
		final JPanel controls = new JPanel( layout );

		btnFetch = new JButton( "fetch segments" );
		btnFetch.addActionListener( this );
		btnRandCosts = new JButton( "set random costs" );
		btnRandCosts.addActionListener( this );
		btnFetchTrain = new JButton( "fetch training data" );
		btnFetchTrain.addActionListener( this );
		btnManClassification = new JButton( "show for classification" );
		btnManClassification.addActionListener( this );

		controls.add( btnFetch, "span, growx, wrap" );
		controls.add( btnRandCosts, "span, growx, wrap" );
		controls.add( btnFetchTrain, "span, growx, wrap" );
		controls.add( btnManClassification, "span, growx, wrap" );

		final JSplitPane splitPane = new JSplitPane( JSplitPane.HORIZONTAL_SPLIT, controls, panel );
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
		} else if ( e.getSource().equals( btnFetchTrain ) ) {
			actionFetchTrain();
		} else if ( e.getSource().equals( btnManClassification ) ) {
			actionShowTrainSegment();
		}
	}

	private void actionShowTrainSegment() {
		MetaSegLog.log.info( "Running manual classification routine..." );
		model.showTrainSegment();
//		model.populateTrainingBdv();

	}

	private void actionFetchTrain() {
		MetaSegLog.log.info( "Fetching random segments for manual classification..." );
		model.getRandomlySelectedSegmentHypotheses();
	}

	private void actionFetch() {
		MetaSegLog.log.info( "Fetching segmentation results..." );
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
	public void focusGained( FocusEvent e ) {
		// TODO Auto-generated method stub

	}

	@Override
	public void focusLost( FocusEvent e ) {
		// TODO Auto-generated method stub

	}
}
