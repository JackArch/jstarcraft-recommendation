package com.jstarcraft.recommendation.data;

import java.util.Iterator;
import java.util.LinkedList;

import com.jstarcraft.ai.data.attribute.QualityAttribute;
import com.jstarcraft.core.common.conversion.csv.ConversionUtility;

public class QualityFeature implements DataFeature<Integer> {

	public final static int DEFAULT_CAPACITY = 10000;

	private QualityAttribute attribute;

	/** 容量 */
	private int capacity;

	/** 数组 */
	private int[] current;

	/** 特征名 */
	private String name;

	/** 特征值 */
	private LinkedList<int[]> values;

	/** 大小 */
	private int size;

	public QualityFeature(String name, QualityAttribute attribute) {
		this.attribute = attribute;
		this.capacity = DEFAULT_CAPACITY;
		this.name = name;
		this.values = new LinkedList<>();
		this.size = 0;
	}

	@Override
	public void associate(Object data) {
		int position = size++ % capacity;
		if (position == 0) {
			current = new int[capacity];
			values.add(current);
		}
		data = ConversionUtility.convert(data, attribute.getType());
		current[position] = attribute.convertData((Comparable) data);
	}

	@Override
	public QualityAttribute getAttribute() {
		return attribute;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public int getSize() {
		return size;
	}

	@Override
	public Iterator<Integer> iterator() {
		return new DiscreteFeatureIterator();
	}

	private class DiscreteFeatureIterator implements Iterator<Integer> {

		private int cursor = 0;

		private int[] current;

		private final Iterator<int[]> iterator = values.iterator();

		@Override
		public boolean hasNext() {
			return cursor < size;
		}

		@Override
		public Integer next() {
			int position = cursor++ % capacity;
			if (position == 0) {
				current = iterator.next();
			}
			return current[position];
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

	}

}
