package edu.trincoll.manswell.portfolio.tools;

import java.util.List;
import java.util.Map;

/** What the loop needs from a model. Tests script it; production is OpenRouterClient. */
public interface ChatModel {

    ModelTurn send(List<Map<String, Object>> messages, List<Map<String, Object>> tools);
}
