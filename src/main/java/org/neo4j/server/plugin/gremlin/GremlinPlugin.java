/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.server.plugin.gremlin;

import com.tinkerpop.blueprints.pgm.impls.neo4j.Neo4jGraph;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.server.plugins.*;
import org.neo4j.server.rest.repr.BadInputException;
import org.neo4j.server.rest.repr.Representation;
import org.neo4j.server.rest.repr.ValueRepresentation;
import org.neo4j.server.rest.repr.formats.JsonFormat;

import javax.script.*;
import java.util.Collections;
import java.util.Map;

/* This is a class that will represent a server side
 * Gremlin plugin and will return JSON
 * for the following use cases:
 * Add/delete vertices and edges from the graph.
 * Manipulate the graph indices.
 * Search for elements of a graph.
 * Load graph data from a file or URL.
 * Make use of JUNG algorithms.
 * Make use of SPARQL queries over OpenRDF-based graphs.
 * and much, much more.
 */

@Description("A server side Gremlin plugin for the Neo4j REST server")
public class GremlinPlugin extends ServerPlugin {

    private final String g = "g";
    private volatile ScriptEngine engine;
    private final EngineReplacementDecision engineReplacementDecision = new CountingEngineReplacementDecision(500);
    private final GremlinToRepresentationConverter gremlinToRepresentationConverter = new GremlinToRepresentationConverter();

    private ScriptEngine createQueryEngine() {
        return new ScriptEngineManager().getEngineByName("gremlin");
    }

    @Name("execute_script")
    @Description("execute a Gremlin script with 'g' set to the Neo4jGraph and 'results' containing the results. Only results of one object type is supported.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation executeScript(
            @Source final GraphDatabaseService neo4j,
            @Description("The Gremlin script") @Parameter(name = "script", optional = false) final String script,
            @Description("JSON Map of additional parameters for script variables") @Parameter(name = "params", optional = true) final String params) {

        try {
            engineReplacementDecision.beforeExecution(script);

            final Bindings bindings = createBindings(neo4j, params);

            final Object result = engine().eval(script, bindings);
            return gremlinToRepresentationConverter.convert(result);
        } catch (final ScriptException e) {
            return ValueRepresentation.string(e.getMessage());
        }
    }

    private Bindings createBindings(GraphDatabaseService neo4j, String params) {
        final Bindings bindings = createInitialBinding(neo4j);
        bindings.putAll(parseParams(params));
        return bindings;
    }

    private Map<String, Object> parseParams(String params) {
        if (params == null || params.isEmpty()) return Collections.emptyMap();
        try {
            return new JsonFormat().readMap(params);
        } catch (BadInputException e) {
            throw new RuntimeException("Error parsing JSON parameter map",e);
        }
    }

    private Bindings createInitialBinding(GraphDatabaseService neo4j) {
        final Bindings bindings = new SimpleBindings();
        final Neo4jGraph graph = new Neo4jGraph(neo4j);
        bindings.put(g, graph);
        return bindings;
    }

    private ScriptEngine engine() {
        if (this.engine == null || engineReplacementDecision.mustReplaceEngine()) {
            this.engine = createQueryEngine();
        }
        return this.engine;
    }

    public Representation getRepresentation(final Object data) {
        return gremlinToRepresentationConverter.convert(data);
    }

}
