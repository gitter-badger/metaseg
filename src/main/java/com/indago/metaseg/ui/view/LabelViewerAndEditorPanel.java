package com.indago.metaseg.ui.view;

import javax.swing.JButton;

import org.scijava.Context;

import com.indago.fg.Assignment;
import com.indago.metaseg.ui.model.MetaSegSolverModel;
import com.indago.metaseg.ui.model.MetaSegTags;
import com.indago.pg.IndicatorNode;
import com.indago.pg.segments.SegmentNode;

import net.imagej.patcher.LegacyInjector;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.roi.IterableRegion;
import net.imglib2.roi.Regions;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelingType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.view.Views;
import sc.fiji.labeleditor.core.model.LabelEditorModel;
import sc.fiji.labeleditor.plugin.behaviours.select.ConflictSelectionBehaviours;
import sc.fiji.labeleditor.plugin.mode.timeslice.TimeSliceLabelEditorBdvPanel;
import sc.fiji.labeleditor.plugin.mode.timeslice.TimeSliceLabelEditorModel;

public class LabelViewerAndEditorPanel< T extends RealType< T > > extends TimeSliceLabelEditorBdvPanel {

	private final MetaSegSolverModel model;
	private JButton btnContinueMetatrain;
	private JButton btnExportSegCompatibleImages;
	private JButton btnExportLabelFusionProblem;

	static {
		LegacyInjector.preinit();
	}

	public LabelViewerAndEditorPanel( MetaSegSolverModel solutionModel ) {
		this.model = solutionModel;
		Context context = solutionModel.getModel().getContext();
		context.inject( this );
		if ( !solutionModel.getModel().is2D() ) {
			this.setMode3D( true );
		}
		init( solutionModel.getModel().getRawData() );
	}

	public void populateBdv( MetaSegSolverModel solutionModel ) {
		LabelEditorModel labelEditorModel = buildLabelEditorModel( solutionModel );
		init( labelEditorModel );
		control().install( new ConflictSelectionBehaviours< T >() );
	}

	public static LabelEditorModel buildLabelEditorModel( MetaSegSolverModel model ) {
		ArrayImg< IntType, IntArray > backing = null;
		if ( model.getModel().is2D() ) {
			if ( model.getModel().getNumberOfFrames() > 1 ) {
				backing =
						ArrayImgs.ints( model.getRawData().dimension( 0 ), model.getRawData().dimension( 1 ), model.getRawData().dimension( 2 ) );
			} else {
				backing =
						ArrayImgs.ints( model.getRawData().dimension( 0 ), model.getRawData().dimension( 1 ) );
			}

		} else {
			if ( model.getModel().getNumberOfFrames() > 1 ) {
				backing =
						ArrayImgs.ints(
								model.getRawData().dimension( 0 ),
								model.getRawData().dimension( 1 ),
								model.getRawData().dimension( 2 ),
								model.getRawData().dimension( 3 ) );
			} else {
				backing =
						ArrayImgs.ints( model.getRawData().dimension( 0 ), model.getRawData().dimension( 1 ), model.getRawData().dimension( 2 ) );
			}

		}

		ImgLabeling< WrappedSegmentNode, IntType > labels = new ImgLabeling<>( backing );
		int timeDimension = model.getModel().getTimeDimensionIndex();
		TimeSliceLabelEditorModel labelEditorModel = new TimeSliceLabelEditorModel<>( labels, model.getRawData(), timeDimension );
		RandomAccessibleInterval< LabelingType< WrappedSegmentNode > > slice;

		for ( int bdvTime = 0; bdvTime < model.getModel().getNumberOfFrames(); bdvTime++ ) {
			if ( model.getPgSolutions() != null && model.getPgSolutions().size() > bdvTime && model.getPgSolutions().get( bdvTime ) != null ) {
				if ( model.getModel().getNumberOfFrames() > 1 ) {
					slice = Views.hyperSlice( labels, timeDimension, bdvTime );
				} else {
					slice = labels;
				}

				final Assignment< IndicatorNode > solution = model.getPgSolution( bdvTime );
				if ( solution != null ) {
					for ( final SegmentNode segVar : model.getProblems().get( bdvTime ).getSegments() ) {
						WrappedSegmentNode wrappedSegVar = new WrappedSegmentNode( segVar );
						IterableRegion< ? > region = segVar.getSegment().getRegion();
						Regions.sample( region, slice ).forEach( t -> t.add( wrappedSegVar ) );
						MetaSegTags tag;
						if ( solution.getAssignment( segVar ) == 1 ) {
							labelEditorModel.tagging().addTagToLabel( MetaSegTags.ILP_APPROVED, wrappedSegVar );
						} else {
							tag = MetaSegTags.ILP_DISAPPROVED;
						}

					}
				}

			}
			System.out.println(labelEditorModel.tagging().get().size());
		}

		labelEditorModel.colors().getFocusBorderColor().set( 255, 0, 0, 150);
		labelEditorModel.colors().getDefaultFaceColor().set(0,0,0,0);
		labelEditorModel.colors().getDefaultBorderColor().set(0,0,0,0);
		labelEditorModel.colors().getFaceColor( MetaSegTags.ILP_APPROVED ).set( 0, 255, 0, 150 );

		return labelEditorModel;
	}
}