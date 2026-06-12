# emottak-event-manager
Tjeneste for hendelsesbehandling i eMottak økosystemet.
- Mottak av hendelser skjer via Kafka (`EventReceiver.kt`) som lagres til hendelsesdatabasen.
- REST-grensesnittet består kun av leseoperasjoner, og benyttes av `emottak-monitor` for fremstilling av hendelser.

## Kafka
| Topic | Retning | Beskrivelse                                                                           |
|---|---|---------------------------------------------------------------------------------------|
| `team-emottak.ebms.message.details` | inn | Kø for opprettelse av en ny MessageDetail                   |
| `team-emottak.event.log` | inn | Kø for hendelser slik som `Melding mottatt via SMTP`, `Melding validert mot CPA`, osv |


## REST-API
| Metode | Endepunkt | Beskrivelse                                                                                                                                                    |
|---|---|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GET` | `/filter-values` | Benyttes av `emottak-monitor` til å hente verdier til dropdowns for å kunne filtrere meldinger/hendelser på rolle/service/action.                              |
| `GET` | `/events` | Benyttes av `emottak-monitor` til å hente hendelser fra nye emottak, med filtreringsmuligheter.                                                                |
| `GET` | `/message-details` | Benyttes av `emottak-monitor` til å hente meldinger fra nye emottak, med filtreringsmuligheter.                                                                |
| `GET` | `/message-details/{$ID}` | Benyttes av `emottak-monitor` til å finne en bestemt MessageDetail. Støtter `requestId` og `mottakId` som input-`ID`.                                          |
| `GET` | `/message-details/{$ID}/events` | Benyttes av `emottak-monitor` til å hente alle hendelsene tilknyttet en MessageDetail (når du klikker på en `mottakId` i en meldinger- eller hendelser-liste). |
| `GET` | `/conversation-status` | Benyttes av `emottak-monitor` til `Conversation-status ebms`-viewet, for å vise siste status pr conversation.                                                  |
| `GET` | `/internal/health/liveness` | Liveness-sjekk                                                                                                                                                 |
| `GET` | `/internal/health/readiness` | Readiness-sjekk                                                                                                                                                |
| `GET` | `/prometheus` | Prometheus-metrikker                                                                                                                                           |

## Database
Bruker PostgreSQL for lagring av meldinger og hendelser: `emottak-event-manager-db`. Skjemamigrering håndteres av Flyway.
