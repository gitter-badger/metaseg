package com.indago.metaseg.ui.view;

import com.indago.pg.segments.SegmentNode;

public class WrappedSegmentNode {

	private SegmentNode segVar;

	public WrappedSegmentNode( SegmentNode segVar ) {
		this.segVar = segVar;
	}

	public SegmentNode getSegVar() {
		return segVar;
	}

	@Override
	public String toString() {
		return Double.toString( segVar.getCost() );
	}

}
