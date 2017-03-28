package org.evomaster.core.problem.rest.service

import com.google.inject.Inject
import org.evomaster.clientJava.controllerApi.EMTestUtils
import org.evomaster.clientJava.controllerApi.dto.SutInfoDto
import org.evomaster.core.problem.rest.*
import org.evomaster.core.problem.rest.auth.NoAuth
import org.evomaster.core.problem.rest.param.BodyParam
import org.evomaster.core.problem.rest.param.FormParam
import org.evomaster.core.search.ActionResult
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.FitnessValue
import org.evomaster.core.search.service.FitnessFunction
import org.glassfish.jersey.client.HttpUrlConnectorProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import javax.annotation.PostConstruct
import javax.ws.rs.client.Client
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Entity
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response


class RestFitness : FitnessFunction<RestIndividual>() {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(RestFitness::class.java)
    }

    @Inject
    private lateinit var rc: RemoteController

    private val client: Client = ClientBuilder.newClient()

    private lateinit var infoDto: SutInfoDto


    @PostConstruct
    private fun initialize() {

        //workaround bug in Jersey client
        client.property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true)

        val started = rc.startSUT()
        if (!started) {
            throw IllegalStateException("Cannot communicate with remote REST controller")
        }

        infoDto = rc.getSutInfo() ?: throw IllegalStateException("Cannot retrieve SUT info")
    }


    override fun doCalculateCoverage(individual: RestIndividual): EvaluatedIndividual<RestIndividual> {

        rc.resetSUT()

        val actionResults: MutableList<ActionResult> = mutableListOf()

        //used for things like chaining "location" paths
        val chainState = mutableMapOf<String, String>()

        //run the test
        for (a in individual.actions) {

            var ok = false

            if (a is RestCallAction) {
                ok = handleRestCall(a, actionResults, chainState)
            } else {
                throw IllegalStateException("Cannot handle: " + a.javaClass)
            }

            if(!ok){
                break
            }
        }

        /*
            We cannot request all non-covered targets, because:
            1) performance hit
            2) might not be possible to have a too long URL
         */
        //TODO prioritized list
        val ids = randomness.choose(archive.notCoveredTargets(), 100)


        val dto = rc.getTargetCoverage(ids) ?:
                throw IllegalStateException("Cannot retrieve coverage")

        val fv = FitnessValue(individual.size().toDouble())

        dto.targets.forEach { t ->

            if (t.descriptiveId != null) {
                idMapper.addMapping(t.id, t.descriptiveId)
            }

            fv.updateTarget(t.id, t.value)
        }

        handleResponseTargets(fv, individual.actions, actionResults)

        return EvaluatedIndividual(fv, individual.copy() as RestIndividual, actionResults)
    }

    /**
     * Create local targets for each HTTP status code in each
     * API entry point
     */
    private fun handleResponseTargets(
            fv: FitnessValue,
            actions: MutableList<RestAction>,
            actionResults: MutableList<ActionResult>) {

        (0 until actionResults.size)
                .filter { actions[it] is RestCallAction }
                .filter { actionResults[it] is RestCallResult }
                .forEach {
                    val status = (actionResults[it] as RestCallResult)
                            .getStatusCode() ?: -1
                    val desc = "$status:${actions[it].getName()}"
                    val id = idMapper.handleLocalTarget(desc)
                    fv.updateTarget(id, 1.0)
                }
    }


    /**
     * @return whether the call was OK. Eg, in some cases, we might want to stop
     * the test at this action, and do not continue
     */
    private fun handleRestCall(a: RestCallAction,
                               actionResults: MutableList<ActionResult>,
                               chainState: MutableMap<String, String>)
            : Boolean {

        var baseUrl = infoDto.baseUrlOfSUT
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length - 1)
        }


        val fullUri = if (a.locationId != null) {
            val locationHeader = chainState[locationName(a.locationId!!)]
                    ?: throw IllegalStateException("Call expected a missing chained 'location'")

            EMTestUtils.resolveLocation(locationHeader, baseUrl + a.path.toString())!!
        } else {
            val path = a.path.resolve(a.parameters)
            baseUrl + path
        }.let {
            /*
                TODO this will be need to be done properly, and check if
                it is or not a valid char
             */
            it.replace("\"", "")
        }

        val builder = client.target(fullUri).request()

        if (a.auth !is NoAuth) {
            a.auth.headers.forEach { h ->
                builder.header(h.name, h.value)
            }
        }

        /*
            TODO: When handling headers, check that they do not
            conflict with the auth ones
         */

        /*
            TODO: need to handle "accept" of returned resource
         */

        /*
           TODO: need to handle also other formats in the body,
           not just JSON and forms
         */
        val body = a.parameters.find { p -> p is BodyParam }
        val forms = a.parameters
                .filter { p -> p is FormParam }
                .map { p ->
                    /*
                        Note: in swagger the "consume" type might be missing.
                        So, if for any reason there is a form param, then consider
                        the body as an application/x-www-form-urlencoded

                        see https://www.w3.org/TR/html401/interact/forms.html#h-17.13.4.1
                        Note: that is old HTML 4, still dealing with RFC 1738, from 1994...

                        HTML 5.1 (November 2016) has these rules:
                        https://www.w3.org/TR/html/sec-forms.html#urlencoded-form-data

                        which unfortunately are unreadable...

                        Regarding URLEncoder in Java 8, it refers to URIs from RFC 2396 from
                        1998 (updating RFC 1738), which is obsoleted by RFC 3986 since 2005!!!

                        Plus, x-www-form-urlencoded and encoding of URIs are not the same!!!

                        REALLY: WTF?!?
                     */
                    val name = URLEncoder.encode(p.gene.getVariableName(), "UTF-8")
                    val value = URLEncoder.encode(p.gene.getValueAsString(), "UTF-8")
                    "$name=$value"
                }
                .joinToString("&")

        val bodyEntity = when {
            body != null -> Entity.json(body.gene.getValueAsString())
            !forms.isBlank() -> Entity.entity(forms, MediaType.APPLICATION_FORM_URLENCODED_TYPE)
            else -> Entity.json("") //FIXME
        }


        val invocation = when (a.verb) {
            HttpVerb.GET -> builder.buildGet()
            HttpVerb.POST -> builder.buildPost(bodyEntity)
            HttpVerb.PUT -> builder.buildPut(bodyEntity)
            HttpVerb.DELETE -> builder.buildDelete()
            HttpVerb.PATCH -> builder.build("PATCH", bodyEntity)
            HttpVerb.OPTIONS -> builder.build("OPTIONS")
            HttpVerb.HEAD -> builder.build("HEAD")
        }

        val response = invocation.invoke()

        val rcr = RestCallResult()
        rcr.setStatusCode(response.status)

        if (response.hasEntity()) {
            if (response.mediaType != null) {
                rcr.setBodyType(response.mediaType)
            }
            try {
                val body = response.readEntity(String::class.java)
                rcr.setBody(body)
            } catch (e: Exception) {
                log.warn("Failed to parse HTTP response: ${e.message}")
            }

        }

        actionResults.add(rcr)

        if (response.status == 401 && a.auth !is NoAuth) {
            //this would likely be a misconfiguration in the SUT controller
            log.warn("Got 401 although having auth for '${a.auth.name}'")
        }


        if (a.saveLocation) {

            if (!response.statusInfo.family.equals(Response.Status.Family.SUCCESSFUL)) {
                /*
                    If this failed, and following actions require the "location" header
                    of this call, there is no point whatsoever to continue evaluating
                    the remaining calls
                 */
                rcr.stopping = true
                return false
            }

            //TODO check if there is name class for locations

            //save location for the following REST calls
            chainState[locationName(a.path.lastElement())] =
                    response.getHeaderString("location") ?: ""
        }

        return true
    }

    private fun locationName(id: String): String {
        return "location_$id"
    }
}