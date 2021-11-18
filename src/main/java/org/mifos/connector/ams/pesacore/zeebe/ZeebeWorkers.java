package org.mifos.connector.ams.pesacore.zeebe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultExchange;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mifos.connector.ams.pesacore.zeebe.ZeebeUtil.zeebeVariablesToCamelProperties;
import static org.mifos.connector.ams.pesacore.zeebe.ZeebeVariables.PARTY_LOOKUP_FAILED;
import static org.mifos.connector.ams.pesacore.zeebe.ZeebeVariables.TRANSFER_SETTLEMENT_FAILED;

@Component
public class ZeebeWorkers {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ZeebeClient zeebeClient;

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ObjectMapper objectMapper;

    // This value determines if Pesa API calls are to be made
    @Value("${ams.local.enabled:false}")
    private boolean isAmsLocalEnabled;

    @Value("${zeebe.client.evenly-allocated-max-jobs}")
    private int workerMaxJobs;

    @PostConstruct
    public void setupWorkers() {

        zeebeClient.newWorker()
                .jobType("transfer-validation")
                .handler((client, job) -> {
                    logWorkerDetails(job);
                    if (isAmsLocalEnabled) {
                        Exchange ex = new DefaultExchange(camelContext);
                        // Do stuff here
                        producerTemplate.send("direct:transfer-validation", ex);
                    } else {
                        Map<String, Object> variables = new HashMap<>();
                        variables.put(PARTY_LOOKUP_FAILED, false);
                        zeebeClient.newCompleteCommand(job.getKey())
                                .variables(variables)
                                .send();
                    }
                })
                .name("transfer-validation")
                .maxJobsActive(workerMaxJobs)
                .open();

        zeebeClient.newWorker()
                .jobType("transfer-settlement")
                .handler((client, job) -> {
                    logWorkerDetails(job);
                    if (isAmsLocalEnabled) {
                        Exchange ex = new DefaultExchange(camelContext);
                        // Do stuff here
                        producerTemplate.send("direct:transfer-settlement", ex);
                    } else {
                        Map<String, Object> variables = new HashMap<>();
                        variables.put(TRANSFER_SETTLEMENT_FAILED, false);
                        zeebeClient.newCompleteCommand(job.getKey())
                                .variables(variables)
                                .send();
                    }
                })
                .name("transfer-settlement")
                .maxJobsActive(workerMaxJobs)
                .open();

    }

    private void logWorkerDetails(ActivatedJob job) {
        JSONObject jsonJob = new JSONObject();
        jsonJob.put("bpmnProcessId", job.getBpmnProcessId());
        jsonJob.put("elementInstanceKey", job.getElementInstanceKey());
        jsonJob.put("jobKey", job.getKey());
        jsonJob.put("jobType", job.getType());
        jsonJob.put("workflowElementId", job.getElementId());
        jsonJob.put("workflowDefinitionVersion", job.getProcessDefinitionVersion());
        jsonJob.put("workflowKey", job.getProcessDefinitionKey());
        jsonJob.put("workflowInstanceKey", job.getProcessInstanceKey());
        logger.info("Job started: {}", jsonJob.toString(4));
    }

}
