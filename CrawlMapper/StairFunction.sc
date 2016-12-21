// A "stair" or "terraced" function.
// Right-continuous step function where the difference between contiguous regions is 1. The function is represented as a list of positions and step directions.
StairFunction {
	var <>startValue, <stepPositions, <stepDirections, <stepCount;

	*new {
		arg startValue = 0;
		^super.new.pr_init_stairFunction(startValue);
	}

	pr_init {
	pr_init_stairFunction {
		arg startValue;
		this.startValue = startValue;
		stepPositions = [];
		stepDirections = [];
		stepCount = 0;
		this.pr_sortSteps();
	}

	pr_sortSteps {
		var order = stepPositions.order;
		stepPositions = stepPositions[order];
		stepDirections = stepDirections[order];
	}

	add {
		arg position, direction;
		position = position.asFloat;
		if(stepPositions.includes(position)) {
			Error("add: requested step position already occupied").throw;
		};
		if(direction == 0) {
			Error("add: cannot add a step with direction 0").throw;
		};
		stepPositions = stepPositions.add(position);
		stepDirections = stepDirections.add(direction);
		this.pr_sortSteps();
		stepCount = stepCount + 1;
	}

	addAll {
		arg positions, directions;
		positions = positions.collect(_.asFloat);
		if(positions.size != directions.size) {
			Error("stepPositions and stepDirections must be the same size").throw;
		};
		if(stepPositions.includesAny(positions)) {
			Error("addAll: sets must be disjoint. Common elements: %".format(stepPositions.sect(positions))).throw;
		};
		stepPositions = stepPositions.addAll(positions);
		stepDirections = stepDirections.addAll(directions);
		this.pr_sortSteps();
		stepCount = stepCount + positions.size;
	}

	remove {
		arg position;
		var index = stepPositions.indexOf(position.asFloat);
		if(index!=nil) {
			this.removeAt(index);
			^true;
		};
		^false;
	}

	removeAt {
		arg index;
		if(index < 0 || index >= stepCount) {
			Error("index out of range").throw;
		};
		stepPositions.removeAt(index);
		stepDirections.removeAt(index);
		stepCount = stepCount - 1;
	}

	clear {
		stepPositions = [];
		stepDirections = [];
		stepCount = 0;
	}

	heightAt {
		arg x;
		var i = 0, value = startValue;
		while {i < stepPositions.size} {
			var position, direction;
			position = stepPositions[i];
			if(position > x) {^value};
			direction = stepDirections[i];
			value = value + direction.sign;
			i = i+1;
		};
		^value;
	}

	stepAt {
		arg i;
		if(i < 0 || (i >= stepCount)) {
			Error("stepAt: index out of bounds").throw;
		};
		^[stepPositions[i], stepDirections[i]];
	}
}

// A "stair" or "terraced" function with an integer interval domain.
// A minimum step gap is added to limit the distance between any two steps.
DiscreteStairFunction : StairFunction {
	var <leftBound, <rightBound, <minStepGap, <freeIntervals;

	*new {
		arg startValue = 0, leftBound = 0, rightBound, minStepGap = 1;
		^super.new(startValue).pr_init_discreteStairFunction(leftBound, rightBound, minStepGap);
	}

		arg leftBound, rightBound, minStepGap;
		if(leftBound >= rightBound) {
	pr_init_discreteStairFunction {
			Error("leftBound must be strictly less than rightBound").throw;
		};
		this.leftBound = leftBound;
		this.rightBound = rightBound;
		if(minStepGap <= 0) {
			Error("minStepGap must be at least 1").throw;
		};
		this.minStepGap = minStepGap;
		if(minStepGap > (rightBound - leftBound)) {
			freeIntervals = [];
		} {
			freeIntervals = [[leftBound+minStepGap, rightBound-minStepGap]];
		}
	}

	emptySlotCount {
		var count = 0;
		freeIntervals.do {
			|interval|
			count = count + interval.last - interval.first + 1;
		};
		^count;
	}

	// get the position of the nth free slot in the function graph
	positionAtFreeSlotIndex {
		arg index = 0;
		var position = 0;
		var intervalIndex = 0;
		if(index < 0) {
			Error("positionAtFreeSlotIndex: index must be nonnegative");
		};
		while { (intervalIndex < stepCount) && (index >= 0) } {
			var interval = freeIntervals[intervalIndex];
			var intervalSize = interval.last - interval.first + 1;
			if(index < intervalSize) {^interval.first + index};
			index = index - intervalSize;
		};
		if(index < 0) {
			Error("positionAtFreeSlotIndex: index exceeds number of free slots");
		}
	}

	intervalsAtDepth {
		// TODO
	}

	intervalsAtDepthOfAtLeast {
		// TODO
	}

	intervalsAtDepthOfAtMost {
		// TODO
	}

	growToHeight {
		// TODO
	}

	growByHeight {
		// TODO
	}

	maxHeight {
		// TODO
	}

	minHeight {
		// TODO
	}

	finalHeight {
		// TODO
	}
}
