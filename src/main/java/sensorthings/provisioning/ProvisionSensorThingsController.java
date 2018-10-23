package sensorthings.provisioning;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.web.bind.annotation.*;

@RestController
public class ProvisionSensorThingsController {

    @RequestMapping(method = RequestMethod.POST, path = "/provisioning")
    @ResponseBody
    public ProvisionSensorThings provisionGost(@RequestBody String json) {
        return new ProvisionSensorThings(json);
    }
}
