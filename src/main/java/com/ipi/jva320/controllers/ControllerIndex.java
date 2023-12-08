package com.ipi.jva320.controllers;

import com.ipi.jva320.exception.SalarieException;
import com.ipi.jva320.model.SalarieAideADomicile;
import com.ipi.jva320.service.SalarieAideADomicileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;


@Controller
public class ControllerIndex {

    private final SalarieAideADomicileService salarieAideADomicileService;

    @Autowired
    public ControllerIndex(SalarieAideADomicileService salarieAideADomicileService) {
        this.salarieAideADomicileService = salarieAideADomicileService;
    }

    @GetMapping(value = "/")
    public String home(ModelMap modelMap) {
        Long countSalaries = salarieAideADomicileService.countSalaries();
        modelMap.put("nombreSalaries", countSalaries);
        return "home";

    }

    @GetMapping("salaries/{id}")
    public String afficherDetailSalarie(@PathVariable long id, ModelMap modelMap) {
        SalarieAideADomicile salarie = salarieAideADomicileService.getSalarie(id);
        modelMap.put("salarie", salarie);
        return "detail_Salarie";
    }

    @GetMapping("/salaries")
    public String afficherListeSalaries(ModelMap modelMap) {
        List<SalarieAideADomicile> salaries = salarieAideADomicileService.getSalaries();
        modelMap.put("salaries", salaries);
        return "list";
    }

    @GetMapping("/detail")
    public String detailSalarie ( ModelMap modelMap){
        SalarieAideADomicile salarieDet = new SalarieAideADomicile("Jeannette Dupontelle"
                , LocalDate.of(2021, 7, 1),
                LocalDate.now(), 0, 0, 10,
                1, 0);
        modelMap.put("salarie",salarieDet);
        return "detail_Salarie";
    }
    @GetMapping("salaries/aide/new")
    public String afficherFormulaireCreation(ModelMap modelMap) {
        return "new_Salarie";

    }
    @PostMapping("salaries/aide/new")
    public String sauvegarderSalarie(SalarieAideADomicile salarie) throws SalarieException {

        SalarieAideADomicile newSalarie = salarieAideADomicileService.creerSalarieAideADomicile(salarie);
        return "redirect:/salaries/" + newSalarie.getId();


    }
}