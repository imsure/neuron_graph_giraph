package edu.stthomas.gps;

import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import org.apache.giraph.aggregators.DoubleMaxAggregator;
import org.apache.giraph.aggregators.DoubleMinAggregator;
import org.apache.giraph.aggregators.LongSumAggregator;
import org.apache.giraph.conf.ImmutableClassesGiraphConfiguration;
import org.apache.giraph.edge.Edge;
import org.apache.giraph.edge.EdgeFactory;
import org.apache.giraph.graph.Vertex;
import org.apache.giraph.io.VertexReader;
import org.apache.giraph.io.formats.AdjacencyListTextVertexInputFormat;
import org.apache.giraph.io.formats.GeneratedVertexInputFormat;
import org.apache.giraph.io.formats.TextVertexInputFormat;
import org.apache.giraph.io.formats.TextVertexOutputFormat;
import org.apache.giraph.job.GiraphJob;
import org.apache.giraph.master.DefaultMasterCompute;
import org.apache.giraph.worker.WorkerContext;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.util.Tool;
import org.apache.log4j.Logger;

/**
 * A neuronal modeling implementation using Giraph.
 * @author imsure
 *
 */
public class NeuronVertex extends Vertex<IntWritable, 
NeuronWritable, FloatWritable, FloatWritable> {

	private Random randn = new Random();
	private FloatWritable weight = new FloatWritable();

	/** Number of supersteps for this test. */
	// public static final int MAX_SUPERSTEPS = 35;
	
	/** Logger */
	private static final Logger LOG =
			Logger.getLogger(NeuronVertex.class);

	private double getGaussian() {
		return randn.nextGaussian();
	}

	private void neuronEvolution(float current, NeuronWritable neuron) {
		current += neuron.synaptic_sum;
		// Update the membrane potential. Step 0.5 ms for numerical stability. 
		neuron.potential += 0.5 * (0.04*neuron.potential*neuron.potential + 5*neuron.potential
				+ 140 - neuron.recovery + current);
		neuron.potential += 0.5 * (0.04*neuron.potential*neuron.potential + 5*neuron.potential
				+ 140 - neuron.recovery + current);
		// Update membrane recovery variable.
		neuron.recovery += neuron.param_a * (neuron.param_b*neuron.potential - neuron.recovery);

		// Update number of iteration
		neuron.time += 1;
		neuron.synaptic_sum = (float) 0.0;
		neuron.fired = 'N'; // Reset firing status
	}

	@Override
	public void compute(Iterable<FloatWritable> messages) {

		long max_supersteps = Long.parseLong(this.getConf().get("max_supersteps", "50"));
		long current_time_step = getSuperstep();

		if (current_time_step < max_supersteps) {
			/** Sum up the messages from last super step. */
			float weight_sum = 0;
			for (FloatWritable message: messages) {
				weight_sum += message.get();
			}

			/** Update the vertex state for the current iteration. */
			NeuronWritable neuron = getValue();
			neuron.synaptic_sum = weight_sum; // Update synaptic weight summation

			float current = 0;
			String neuron_type = neuron.type.toString();
			// Generate thalamic input.
			if (neuron_type.equals("ce")) {
				current = (float)3.5 * (float)this.getGaussian();
			} else if (neuron_type.equals("ci")) {
				current = 2 * (float)this.getGaussian();
			} else if (neuron_type.equals("tc")) {
				current = 0;
			} else if (neuron_type.equals("stn")) {
				current = (float)0.5 * (float)this.getGaussian();
			} else if (neuron_type.equals("strd1")) {
				current = 0;
			} else if (neuron_type.equals("strd2")) {
				current = 0;
			} else if (neuron_type.equals("gpe")) {
				if (current_time_step <= 1000) { 
					// no current supplied to GPe before 1000ms
					current = 0;
				} 
				if (current_time_step > 1000 && current_time_step <= 2500) { 
					// supply current to GPe in channel 1
					if (neuron.channel == 1) {
						current = (float) 9.2 * (float)this.getGaussian();
					}

					// no input to GPe in channel until 2500 ms 
					if (neuron.channel == 2) {
						current = 0;
					}
				} 

				if (current_time_step > 2500) { 
					// supply current to GPe in channel 2 after 2500 ms
					if (neuron.channel == 2) { 
						current = (float) 17.5 * (float)this.getGaussian();
					}

					// the same input to channel 1 as previous
					if (neuron.channel == 1) {
						current = (float) 9.2 * (float)this.getGaussian();
					}
				}
			} else if (neuron_type.equals("gpi")) {
				current = 15 * (float)this.getGaussian();
			} else {
				System.err.println("No neuron type matched!!!: " + neuron.type.toString());
			}

			this.neuronEvolution(current, neuron); 	// Start Neuron Evolution

			/** If a neuron fired, send the messages to its outgoing neurons. */
			if (neuron.potential >= 30.0) {
				for (Edge<IntWritable, FloatWritable> edge : getEdges()) {
					weight.set(edge.getValue().get());
					if (LOG.isDebugEnabled()) {
						LOG.debug("Vertex " + getId() + " sent to " +
								edge.getTargetVertexId() + " = " + weight.get());
					}

					sendMessage(edge.getTargetVertexId(), weight);
				}

				neuron.potential = neuron.param_c; // Reset the membrane potential (voltage)
				neuron.recovery += neuron.param_d; // Reset the membrane recovery variable
				neuron.fired = 'Y'; // Indicate the neuron fired at this iteration.
			}

			setValue(neuron); // Update the value of the vertex.

		} else {
			voteToHalt();
		}
	}
}
