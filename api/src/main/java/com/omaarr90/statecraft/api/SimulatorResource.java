package com.omaarr90.statecraft.api;

import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import static com.omaarr90.statecraft.api.ApiModels.*;

@Path("/api/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class SimulatorResource {
	@Inject
	SimulatorService simulator;
	@GET
	@Path("/capabilities")
	public Capabilities capabilities() {
		return simulator.capabilities();
	}
	@POST
	@Path("/parse")
	public ParseResponse parse(ParseRequest request) {
		return simulator.parse(request);
	}
	@POST
	@Path("/simulate")
	public SimulateResponse simulate(SimulateRequest request) {
		return simulator.simulate(request);
	}
	@POST
	@Path("/collapse")
	public CollapseResponse collapse(CollapseRequest request) {
		return simulator.collapse(request);
	}
	@POST
	@Path("/noise")
	public NoiseResponse noise(NoiseRequest request) {
		return simulator.noise(request);
	}
}
