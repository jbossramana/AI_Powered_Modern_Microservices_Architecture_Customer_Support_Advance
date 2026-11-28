# API and Sequence Reference

## Saga happy path

```mermaid
sequenceDiagram
 participant C as Client
 participant O as Order/Saga
 participant D as Drools
 participant I as Inventory
 participant P as Payment
 C->>O: POST /api/orders
 O->>D: Evaluate policy
 D-->>O: AUTO_APPROVE
 O->>I: reserve(sagaId)
 I-->>O: RESERVED
 O->>P: authorize(sagaId)
 P-->>O: AUTHORIZED
 O-->>C: COMPLETED
```

## Saga compensation

```mermaid
sequenceDiagram
 participant O as Order/Saga
 participant I as Inventory
 participant P as Payment
 O->>I: reserve
 I-->>O: RESERVED
 O->>P: authorize
 P-->>O: DECLINED
 O->>I: release (compensation)
 I-->>O: RELEASED
 O-->>O: Mark compensated/failed
```

## AI endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/knowledge/ingest` | Add a knowledge document |
| POST | `/api/knowledge/ask` | RAG question answering |
| POST | `/api/agent/run` | Goal-oriented tool-using support agent |
| POST | `/api/ml/predict-priority` | Supervised priority prediction (FastAPI) |
