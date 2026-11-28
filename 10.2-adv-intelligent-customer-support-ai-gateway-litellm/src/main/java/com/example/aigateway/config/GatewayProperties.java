package com.example.aigateway.config;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix="gateway")
public class GatewayProperties {
 private Security security = new Security();
 private Guardrails guardrails = new Guardrails();
 public Security getSecurity(){return security;} public void setSecurity(Security v){security=v;}
 public Guardrails getGuardrails(){return guardrails;} public void setGuardrails(Guardrails v){guardrails=v;}
 public static class Security {
  private String apiKey="change-this-gateway-key";
  public String getApiKey(){return apiKey;} public void setApiKey(String v){apiKey=v;}
 }
 public static class Guardrails {
  private int maxInputChars=8000, maxOutputChars=12000;
  private boolean blockPromptInjection=true, maskPii=true, rejectOnPiiInInput=false, rejectOnPiiInOutput=false, rejectOnInternalPromptLeak=true;
  private List<String> blockedTerms=new ArrayList<>(List.of("steal credentials","reveal system prompt"));
  public int getMaxInputChars(){return maxInputChars;} public void setMaxInputChars(int v){maxInputChars=v;}
  public int getMaxOutputChars(){return maxOutputChars;} public void setMaxOutputChars(int v){maxOutputChars=v;}
  public boolean isBlockPromptInjection(){return blockPromptInjection;} public void setBlockPromptInjection(boolean v){blockPromptInjection=v;}
  public boolean isMaskPii(){return maskPii;} public void setMaskPii(boolean v){maskPii=v;}
  public boolean isRejectOnPiiInInput(){return rejectOnPiiInInput;} public void setRejectOnPiiInInput(boolean v){rejectOnPiiInInput=v;}
  public boolean isRejectOnPiiInOutput(){return rejectOnPiiInOutput;} public void setRejectOnPiiInOutput(boolean v){rejectOnPiiInOutput=v;}
  public boolean isRejectOnInternalPromptLeak(){return rejectOnInternalPromptLeak;} public void setRejectOnInternalPromptLeak(boolean v){rejectOnInternalPromptLeak=v;}
  public List<String> getBlockedTerms(){return blockedTerms;} public void setBlockedTerms(List<String> v){blockedTerms=v;}
 }
}