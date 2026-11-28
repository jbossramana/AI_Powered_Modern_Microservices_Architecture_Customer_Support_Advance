from fastapi import FastAPI
from pydantic import BaseModel, Field
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from typing import Literal
app = FastAPI(title="Support Priority ML", version="1.0.0")
# Small labelled teaching dataset. Replace with governed, representative production data.
TRAINING = [
 ("service unavailable production outage all users down", "CRITICAL"),
 ("payment processing failed many customers affected", "CRITICAL"),
 ("cannot login business account blocked", "HIGH"),
 ("duplicate charge refund requested urgently", "HIGH"),
 ("invoice question billing clarification", "MEDIUM"),
 ("how can I update my profile picture", "LOW"),
 ("feature request change dashboard color", "LOW"),
 ("password reset instructions please", "MEDIUM"),
 ("application slow intermittent timeout", "HIGH"),
 ("minor typo in help documentation", "LOW"),
]
model = Pipeline([("tfidf", TfidfVectorizer(ngram_range=(1,2))), ("classifier", LogisticRegression(max_iter=1000, class_weight="balanced"))])
model.fit([x for x,y in TRAINING],[y for x,y in TRAINING])
class Ticket(BaseModel):
 subject: str
 body: str
 planTier: Literal["FREE","STANDARD","BUSINESS","ENTERPRISE"] = "STANDARD"
 accountAgeDays: int = Field(default=30, ge=0)
 priorTicketCount30d: int = Field(default=0, ge=0)
@app.get("/health")
def health(): return {"status":"UP","model":"TF-IDF + LogisticRegression","trainingSamples":len(TRAINING)}
@app.post("/api/ml/predict-priority")
def predict(ticket: Ticket):
 text=f"{ticket.subject} {ticket.body}"
 prediction=model.predict([text])[0]
 probabilities=model.predict_proba([text])[0]
 confidence=float(max(probabilities))
 # Business signals are explicit, transparent post-model adjustments.
 if ticket.planTier in ("BUSINESS","ENTERPRISE") and prediction=="MEDIUM": prediction="HIGH"
 if ticket.priorTicketCount30d >= 5 and prediction=="LOW": prediction="MEDIUM"
 return {"priority":prediction,"modelConfidence":round(confidence,4),"model":"tfidf-logistic-regression","features":{"planTier":ticket.planTier,"accountAgeDays":ticket.accountAgeDays,"priorTicketCount30d":ticket.priorTicketCount30d},"note":"Educational baseline; calibrate and validate on production-labelled data."}
