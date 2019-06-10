package com.indago.metaseg.ui.util;

import java.io.File;
import java.io.IOException;

import com.indago.fg.Assignment;
import com.indago.io.DataMover;
import com.indago.metaseg.MetaSegLog;
import com.indago.metaseg.ui.model.MetaSegSolverModel;
import com.indago.pg.IndicatorNode;
import com.indago.pg.segments.SegmentNode;

import ij.IJ;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.roi.IterableRegion;
import net.imglib2.roi.Regions;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class SolutionExporter {
	
	public static void exportSegData( final MetaSegSolverModel msSolverModel, File projectFolderBasePath ) {
		try {
			RandomAccessibleInterval< IntType > segImages = createSegData( msSolverModel );
			for ( int image = 0; image < msSolverModel.getModel().getNumberOfFrames(); image++ ) {
				IntervalView< IntType > res = Views.hyperSlice( segImages, msSolverModel.getModel().getTimeDimensionIndex(), image );
				IJ.save(
						ImageJFunctions.wrap( res, "tracking solution" ).duplicate(),
						projectFolderBasePath.getAbsolutePath() + "/mask" + String
								.format( "%03d", image ) + ".tif" );
			}

		} catch ( IOException e ) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static RandomAccessibleInterval< IntType > createSegData(
			MetaSegSolverModel msSolverModel ) throws IOException {

		final RandomAccessibleInterval< IntType > ret =
				DataMover.createEmptyArrayImgLike( msSolverModel.getRawData(), new IntType() );
		//call collectTraData
		if ( msSolverModel.getModel().hasFrames() ) {
			for ( int t = 0; t < msSolverModel.getModel().getNumberOfFrames(); t++ ) {
				final Assignment< IndicatorNode > solution = msSolverModel.getPgSolution( t );
				if ( solution != null ) {
					final IntervalView< IntType > retSlice = Views.hyperSlice( ret, msSolverModel.getModel().getTimeDimensionIndex(), t );

					int curColorId = 1;
					for ( final SegmentNode segVar : msSolverModel.getProblems().get( t ).getSegments() ) {
						if ( solution.getAssignment( segVar ) == 1 ) {
							drawSegmentWithId( retSlice, solution, segVar, curColorId );
							curColorId = curColorId + 1;
						}
					}
				}
			}
		} else {
			final Assignment< IndicatorNode > solution = msSolverModel.getPgSolution( 0 );
			if ( solution != null ) {
				final int curColorId = 1;
				for ( final SegmentNode segVar : msSolverModel.getProblems().get( 0 ).getSegments() ) {
					if ( solution.getAssignment( segVar ) == 1 ) {
						drawSegmentWithId( ret, solution, segVar, curColorId );
					}
				}
			}
		}

		return ret;
	}

	private static void drawSegmentWithId(
			final RandomAccessibleInterval< IntType > imgSolution,
			final Assignment< IndicatorNode > solution,
			final SegmentNode segVar,
			final int curColorId ) {

		if ( solution.getAssignment( segVar ) == 1 ) {
			final int color = curColorId;

			final IterableRegion< ? > region = segVar.getSegment().getRegion();
			final int c = color;
			try {
				Regions.sample( region, imgSolution ).forEach( t -> t.set( c ) );
			} catch ( final ArrayIndexOutOfBoundsException aiaob ) {
				MetaSegLog.log.error( aiaob );
			}
		}
	}
}
