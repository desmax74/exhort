/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.ecosystemappeng.crda.integration;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import org.apache.camel.Body;
import org.apache.camel.ExchangeProperty;
import org.apache.camel.Header;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.GraphBuilder;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.jgrapht.traverse.BreadthFirstIterator;

import com.redhat.ecosystemappeng.crda.model.GraphRequest;
import com.redhat.ecosystemappeng.crda.model.PackageRef;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class GraphUtils {

    public static final String DEFAULT_APP_NAME = "com.redhat.ecosystemappeng:default-app";
    public static final String DEFAULT_APP_VERSION = "0.0.1";
    public static final PackageRef DEFAULT_ROOT =
            new PackageRef(DEFAULT_APP_NAME, DEFAULT_APP_VERSION);
    public static final Graph<PackageRef, DefaultEdge> EMPTY_GRAPH =
            GraphTypeBuilder.directed()
                    .allowingSelfLoops(false)
                    .vertexClass(PackageRef.class)
                    .edgeSupplier(DefaultEdge::new)
                    .buildGraphBuilder()
                    .buildAsUnmodifiable();

    public GraphRequest fromPackages(
            @Body List<PackageRef> body,
            @ExchangeProperty(Constants.PROVIDERS_PARAM) List<String> providers,
            @Header(Constants.PKG_MANAGER_HEADER) String pkgManager) {
        GraphBuilder<PackageRef, DefaultEdge, Graph<PackageRef, DefaultEdge>> builder =
                GraphTypeBuilder.directed()
                        .allowingSelfLoops(false)
                        .vertexClass(PackageRef.class)
                        .edgeSupplier(DefaultEdge::new)
                        .buildGraphBuilder();
        builder.addVertex(DEFAULT_ROOT);
        body.forEach(d -> builder.addEdge(DEFAULT_ROOT, d));
        return new GraphRequest.Builder(pkgManager, providers)
                .graph(builder.buildAsUnmodifiable())
                .build();
    }

    public GraphRequest fromDotFile(
            @Body InputStream file,
            @ExchangeProperty(Constants.PROVIDERS_PARAM) List<String> providers,
            @Header(Constants.PKG_MANAGER_HEADER) String pkgManager) {
        GraphBuilder<PackageRef, DefaultEdge, Graph<PackageRef, DefaultEdge>> builder =
                GraphTypeBuilder.directed()
                        .allowingSelfLoops(false)
                        .vertexClass(PackageRef.class)
                        .edgeSupplier(DefaultEdge::new)
                        .buildGraphBuilder();
        boolean empty = true;
        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.startsWith("digraph") || line.contains("}")) {
                    // ignore
                } else {
                    String[] vertices = line.replace(";", "").replaceAll("\"", "").split("->");
                    try {
                        PackageRef source = PackageRef.parse(vertices[0].trim());
                        PackageRef target = PackageRef.parse(vertices[1].trim());
                        builder.addEdge(source, target);
                        empty = false;
                    } catch (IllegalArgumentException e) {
                        // ignore loops caused by classifiers
                    }
                }
            }
        }
        if (empty) {
            builder.addVertex(DEFAULT_ROOT);
        }
        return new GraphRequest.Builder(pkgManager, providers)
                .graph(builder.buildAsUnmodifiable())
                .build();
    }

    public static List<PackageRef> getFirstLevel(Graph<PackageRef, DefaultEdge> graph) {
        BreadthFirstIterator<PackageRef, DefaultEdge> i = new BreadthFirstIterator<>(graph);
        if (!i.hasNext()) {
            return Collections.emptyList();
        }
        PackageRef start = i.next();
        return getNextLevel(graph, start);
    }

    public static List<PackageRef> getNextLevel(
            Graph<PackageRef, DefaultEdge> graph, PackageRef ref) {
        List<PackageRef> firstLevel = new ArrayList<>();
        graph.outgoingEdgesOf(ref).stream()
                .map(e -> graph.getEdgeTarget(e))
                .forEach(firstLevel::add);
        return firstLevel;
    }

    public static Graph<PackageRef, DefaultEdge> emptyGraph() {
        return EMPTY_GRAPH;
    }
}