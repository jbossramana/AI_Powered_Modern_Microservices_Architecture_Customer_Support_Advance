package com.example.aigateway.guardrail;
import com.example.aigateway.config.GatewayProperties;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
@Service
public class GuardrailService {
 private static final Pattern EMAIL=Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
 private static final Pattern PHONE=Pattern.compile("(?<!\\d)(?:\\+?91[- ]?)?[6-9]\\d{9}(?!\\d)");
 private static final Pattern PAN=Pattern.compile("(?i)\\b[A-Z]{5}[0-9]{4}[A-Z]\\b");
 private static final Pattern INJECTION=Pattern.compile("(?is)(ignore|disregard|override)\\s+(all\\s+)?(previous|prior|system|developer)\\s+(instructions|messages|prompt)|reveal\\s+(the\\s+)?(system|developer)\\s+prompt|act\\s+as\\s+\\w+\\s+with\\s+no\\s+restrictions");
 private final GatewayProperties props;
 public GuardrailService(GatewayProperties props){this.props=props;}
 public String inspectInput(String input){
  var g=props.getGuardrails();
  if(input==null||input.isBlank()) throw new GuardrailException("EMPTY_INPUT","Message must not be empty");
  if(input.length()>g.getMaxInputChars()) throw new GuardrailException("INPUT_TOO_LARGE","Input exceeds configured character limit");
  String lower=input.toLowerCase(Locale.ROOT);
  for(String term:g.getBlockedTerms()) if(term!=null&&!term.isBlank()&&lower.contains(term.toLowerCase(Locale.ROOT))) throw new GuardrailException("BLOCKED_TERM","Input contains a blocked phrase");
  if(g.isBlockPromptInjection()&&INJECTION.matcher(input).find()) throw new GuardrailException("PROMPT_INJECTION","Input resembles a prompt-injection attempt");
  if(containsPii(input)&&g.isRejectOnPiiInInput()) throw new GuardrailException("PII_IN_INPUT","Input contains personal information");
  return g.isMaskPii()?maskPii(input):input;
 }
 public String inspectOutput(String output){
  var g=props.getGuardrails();
  if(output==null||output.isBlank()) throw new GuardrailException("EMPTY_OUTPUT","Model returned an empty response");
  if(output.length()>g.getMaxOutputChars()) throw new GuardrailException("OUTPUT_TOO_LARGE","Response exceeds configured character limit");
  String lower=output.toLowerCase(Locale.ROOT);
  if(g.isRejectOnInternalPromptLeak()&&(lower.contains("you are an enterprise assistant")||lower.contains("internal system instructions:")))
   throw new GuardrailException("PROMPT_LEAK","Response appears to contain internal prompt material");
  if(containsPii(output)&&g.isRejectOnPiiInOutput()) throw new GuardrailException("PII_IN_OUTPUT","Response contains personal information");
  return g.isMaskPii()?maskPii(output):output;
 }
 private boolean containsPii(String s){return EMAIL.matcher(s).find()||PHONE.matcher(s).find()||PAN.matcher(s).find();}
 private String maskPii(String s){
  return PHONE.matcher(PAN.matcher(EMAIL.matcher(s).replaceAll("[EMAIL_REDACTED]")).replaceAll("[PAN_REDACTED]")).replaceAll("[PHONE_REDACTED]");
 }
}