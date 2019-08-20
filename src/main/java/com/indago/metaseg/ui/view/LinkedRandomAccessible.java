package com.indago.metaseg.ui.view;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;

public class LinkedRandomAccessible< T > implements RandomAccessible< T > {

	private RandomAccessible< T > source;

	public LinkedRandomAccessible( RandomAccessible< T > source ) {
		this.source = source;
	}

	public void setSource( RandomAccessibleInterval< T > source ) {
		this.source = source;
	}

	public RandomAccessible< T > getSource() {
		return source;
	}

	@Override
	public RandomAccess< T > randomAccess() {
		return source.randomAccess();
	}

	@Override
	public RandomAccess< T > randomAccess( Interval interval ) {
		return source.randomAccess( interval );
	}

	@Override
	public int numDimensions() {
		return source.numDimensions();
	}
}

