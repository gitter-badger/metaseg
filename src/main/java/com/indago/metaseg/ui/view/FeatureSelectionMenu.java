package com.indago.metaseg.ui.view;

import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import com.indago.metaseg.ui.model.MetaSegModel;

public class FeatureSelectionMenu implements ActionListener, ItemListener {

	private final MetaSegModel model;
	private FeaturesPanel featureSelectionPanel = new FeaturesPanel();

	public FeatureSelectionMenu( final MetaSegModel model ) {
		this.model = model;
	}

	public MenuBar createMenuBar() {
		MenuBar menuBar;
		Menu featuremenu;
		MenuItem menuItem;

		//Create the menu bar.
		menuBar = new MenuBar();

		//Build the file menu.
		featuremenu = new Menu( "Features" );
		featuremenu.getAccessibleContext().setAccessibleDescription( "This is the menu to select features for classifier training" );
		menuBar.add( featuremenu );

		menuItem = new MenuItem( "(Un)Select features" );
		menuItem.getAccessibleContext().setAccessibleDescription( "(Un)Select features" );
		menuItem.addActionListener( this );
		featuremenu.add( menuItem );
		return menuBar;

	}
	@Override
	public void itemStateChanged( ItemEvent e ) {
		// TODO Auto-generated method stub

	}

	@Override

	public void actionPerformed( ActionEvent e ) {
		final MenuItem jmi = ( MenuItem ) e.getSource();
		if ( jmi.getLabel() == "(Un)Select features" ) {
			FeatureSelection fs = featureSelectionPanel.show();
			if ( fs != null )
				model.getCostTrainerModel().getComputeAllFeaturesObject().setFeatureSelection( fs );
		}

	}

	public FeaturesPanel getFeatureSelectionPanel() {
		return featureSelectionPanel;
	}
}
