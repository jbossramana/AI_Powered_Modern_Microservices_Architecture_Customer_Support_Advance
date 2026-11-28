package com.example.agent.graph;

import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
public class XmlWorkflowLoader {

    private final ApplicationContext applicationContext;

    public XmlWorkflowLoader(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @SuppressWarnings("unchecked")
    public CompiledGraph<SupportState> load() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        try (InputStream input = getClass().getResourceAsStream("/support-flow.xml")) {
            if (input == null) {
                throw new IllegalStateException("support-flow.xml not found on the classpath");
            }

            Element root = factory.newDocumentBuilder().parse(input).getDocumentElement();
            StateGraph<SupportState> graph = new StateGraph<>(SupportState.SCHEMA, SupportState::new);

            NodeList nodeElements = root.getElementsByTagName("node");
            for (int i = 0; i < nodeElements.getLength(); i++) {
                Element nodeElement = (Element) nodeElements.item(i);
                String nodeId = requiredAttribute(nodeElement, "id");
                String methodName = requiredAttribute(nodeElement, "bean");

                GraphNodeCatalog catalog = applicationContext.getBean(GraphNodeCatalog.class);
                Method method = Arrays.stream(catalog.getClass().getMethods())
                        .filter(candidate -> candidate.getName().equals(methodName)
                                && candidate.getParameterCount() == 0)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "No zero-argument GraphNodeCatalog method named " + methodName));

                NodeAction<SupportState> action = (NodeAction<SupportState>) method.invoke(catalog);
                graph.addNode(nodeId, node_async(action));
            }

            NodeList edgeElements = root.getElementsByTagName("edge");
            for (int i = 0; i < edgeElements.getLength(); i++) {
                Element edge = (Element) edgeElements.item(i);
                String from = requiredAttribute(edge, "from");
                String to = requiredAttribute(edge, "to");
                graph.addEdge("START".equals(from) ? START : from,
                        "END".equals(to) ? END : to);
            }
            return graph.compile();
        }
    }

    private static String requiredAttribute(Element element, String name) {
        String value = element.getAttribute(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing XML attribute '" + name + "'");
        }
        return value;
    }
}
