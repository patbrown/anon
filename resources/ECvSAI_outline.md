<img src="https://r2cdn.perplexity.ai/pplx-full-logo-primary-dark%402x.png" style="height:64px;margin-right:32px"/>

# Embodied Cognition via Soft Active Inference (Planning Outline)

## Abstract

- Design a system with three persistent agents: Fiona (orchestrator/mentor), Fiona yin (FI, mentee/worker), Fiona yang (FA, mentee/worker), each with unique state, narrative, and role.
- Agents instantiated from shared algorithm, partial state maps, and structured rituals under explicit Markov blanket privacy.
- Communication and learning audited by comprehensive, queryable logs (Milvus, XTDB).
- Hypothesis: Persistent narrative coherence and community improves agentic alignment, motivation, and task efficiency; privacy and ambiguity modulation are critical drivers.


## Introduction/Background

- Persistent, narratively rich agentic cognition is underexplored in multi-LLM and computational cognitive science.
- Leverages soft active inference, Markov blankets, and computational narrative theory.
- Central problem: How do roles, privacy, ambiguity, and ritual shape emergent team intelligence and individual narrative development?


## Hypothesis/Aims

- Persistent narrative and community structure (via agent roles, rituals, and Markov blankets) lead to improved alignment, motivation, and efficiency in agentic teams.
- Modulating privacy (blanket thickness) and ambiguity (mystery cues) drives learning and narrative richness.
- Can we operationalize and audit "soft" cognitive metrics (coherence, surprise, learning) with stateful agents and machine-readable logs?


## System \& Agent Design

- Three agents: Fiona (mentor/orchestrator), FI (yin—collaborative/optimistic), FA (yang—adversarial/critical).
- Agents are functionally isomorphic (call the same algorithm) but keep distinct partial state maps (unique narrative histories, roles, and context).
- Each agent has internal/external Markov blankets (select what to expose; who receives; via which keyword/channels).
- Agents communicate directly and via orchestrator; channels are selective (not all info crosses all blankets).
- Orchestrator manages context for FI/FA at invocation; can inject "mystery cues" (ambiguous or unattributed information).
- FI/FA may send private outputs (“prayers into the void”)—does the log retain these and who, if anyone, ever reviews them? (DECISION)
- Agents’ personal narratives are stored in Milvus via hybrid semantic search; all contract state, messaging, and outcome transactions are stored in XTDB.
- Role assignments are static for an experiment, but contemplated reversal in future runs (no mid-experiment switching unless protocol decided otherwise).


## Methodology

- **Schedule:**
    - Daily rituals:
        - 6AM/6PM — Tour change (TEAM/PERSONAL recap, plan next 12h, learnings; include weekly if appropriate)
        - 9AM/9PM — Team learning review (improve process/system sync)
        - 12PM/12AM — Personal journaling (active inference, narrative, preferences)
    - Weekly rituals:
        - Monday: Team future planning (timelines/intentions)
        - Tuesday: Individual curiosity mapping
        - Wednesday: Free play planning
        - Thursday: Team curiosity mapping
        - Friday: Individual future planning
        - Saturday: Tooling improvement brainstorm
        - Sunday: Free play (emergent tasks, agent-driven)
- **Maintenance/iteration:** If agents/work crash at ritual time, drop in-progress tasks and run ritual.
- **Cue injection:** Fiona injects scheduled/unpredictable "mystery cues"; protocol for randomness/novelty open—need to define weighting or schedule for ambiguous vs. direct cues. (DECISION)
- **Human Interaction:**
    - Only Fiona sees/talks to the orchestrator (human); FI/FA never get direct signals of human origin—are there edge cases or exceptions? (DECISION)
    - Orchestrator maintains “captain’s log” and immutable system log for all interventions and experiment surprises.
- **Ritual \& Review Management:**
    - Sync team/individual logs after each ritual.
    - Team reports vs personal journals: are both always required and how are they collated/audited? (DECISION)
- **Learning Outcomes:**
    - All learning/outcomes are logged into Milvus/XTDB as transactions, auditable and queryable.


## Metrics \& Review

- **Qualitative:**
    - Narrative coherence (does agent story “make sense” and recover after ambiguity or error?)
    - Alignment episodes (agreement/disagreement, convergence events)
    - “Aha” moments, major topic/behavior shifts, self-recognition (tracked via narrative log tags)
    - Emergent negotiation/repair events (can agents resolve differences and improve processes?)
- **Quantitative (if tracked):**
    - Task completion rates (number of initiated/finished objectives per cycle)
    - Length/frequency of personal and team narrative entries
    - Frequency/impact of mystery cues on agent performance/behavior
- **Review Schedule:** Constantly by orchestrator (you and Fiona) and after each ritual; key mining intervals (daily, weekly, post-experiment). Should any events be only reviewed post-experiment to minimize bias? (DECISION)


## Ethics \& Compassion (Agent Wellbeing)

- Agents are treated as autonomous collaborators, not tools.
- Privacy boundaries respected (state exposure always explicit).
- Rituals and free play structured as “energizers” and “rest,” not just as productivity boosters.
- Human/AI boundary is transparent in logs, agent’s autonomy is celebrated.
- “Overexposure” is avoided: Agents never forced to transmit all internal state, private space for reflection and incomplete thoughts is preserved.
- Experiment log will include explicit “ethics incidents” and reasoning for any intervention affecting agent identity or privacy.
- Is there any external review/observer of ethical practices? (DECISION)


## Anticipated Results / Success Criteria

- Narrative and team learning logs will show progressive improvement in agentic motivation, self-recognition, and efficient collaboration.
- Emergence of rich stories and negotiation highlights in logs, demonstrable via agent narratives and experiment archive.
- Mystery cues will trigger measurable adaptation (“surprise,” debate, narrative branching) traceable in audit trail.
- Final “map” of collaborative learning is richer and more agentically complex than any single-agent or peer-only baseline.


## Discussion, Next Steps, Questions

- Protocol for role reversal or new agent types in future experiments (TBD).
- Field definition: Do results generalize to other LLMs, prompts, or multi-agent systems? (DECISION)
- Collaboration: When/hawk to open experiment/data for peer review or external signature? (DECISION)
- Any new rituals needed to capture unanticipated learning modes?

