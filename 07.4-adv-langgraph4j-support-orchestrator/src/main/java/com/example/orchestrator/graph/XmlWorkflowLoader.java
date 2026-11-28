package com.example.orchestrator.graph;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;

@Component
public class XmlWorkflowLoader {

    public record NodeDef(String id, String type) {}
    public record Transition(String from, String to, String condition) {}
    public record Workflow(String start, String end, List<NodeDef> nodes,
                           List<Transition> transitions) {}

    public Workflow load(Resource resource) {
        try (InputStream input = resource.getInputStream()) {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(input);

            Element root = document.getDocumentElement();
            String start = root.getElementsByTagName("start").item(0)
                    .getAttributes().getNamedItem("node").getNodeValue();
            String end = root.getElementsByTagName("end").item(0)
                    .getAttributes().getNamedItem("node").getNodeValue();

            List<NodeDef> nodes = new ArrayList<>();
            NodeList nodeElements = root.getElementsByTagName("node");
            for (int i = 0; i < nodeElements.getLength(); i++) {
                Element e = (Element) nodeElements.item(i);
                nodes.add(new NodeDef(e.getAttribute("id"), e.getAttribute("type")));
            }

            List<Transition> transitions = new ArrayList<>();
            NodeList edgeElements = root.getElementsByTagName("transition");
            for (int i = 0; i < edgeElements.getLength(); i++) {
                Element e = (Element) edgeElements.item(i);
                transitions.add(new Transition(
                        e.getAttribute("from"),
                        e.getAttribute("to"),
                        e.getAttribute("condition")));
            }
            return new Workflow(start, end, nodes, transitions);
        } catch (Exception e) {
            throw new IllegalStateException("Could not load XML workflow", e);
        }
    }
}
