#!/bin/sh

hadoop fs -rm -r neuron_output

hadoop jar giraph_neuron_graph-1.0.jar org.apache.giraph.GiraphRunner -Dgiraph.zkList=m02.gps.stthomas.edu:2181 -Dmax_supersteps=100 edu.stthomas.gps.NeuronVertex -vif edu.stthomas.gps.NeuronVertexTextInputFormat -vip neuron_input -of edu.stthomas.gps.NeuronVertexTextOutputFormat -op neuron_output -c org.apache.giraph.combiner.FloatSumCombiner -w 100
