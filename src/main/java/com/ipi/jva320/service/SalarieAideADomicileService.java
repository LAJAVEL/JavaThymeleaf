package com.ipi.jva320.service;

import com.ipi.jva320.exception.ExceptionS;
import com.ipi.jva320.model.Entreprise;
import com.ipi.jva320.model.SalarieAideADomicile;
import com.ipi.jva320.repository.SalarieAideADomicileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Permet de gérer les salariés aide à domicile, et notamment :
 * en créer, leur ajouter des congés (ajouteConge()), et les mettre à jour à la clôture de mois et d'année.
 */
@Service
public class SalarieAideADomicileService {

    @Autowired
    private SalarieAideADomicileRepository salarieAideADomicileRepository;

    public SalarieAideADomicileService() {
    }

    /**
     * @return le nombre de salariés dans la base
     */
    public Long countSalaries() {
        return salarieAideADomicileRepository.count();
    }

    /**
     * @return le nombre de salariés dans la base
     */
    public List<SalarieAideADomicile> getSalaries() {
        return StreamSupport.stream(salarieAideADomicileRepository.findAll().spliterator(), false)
                .collect(Collectors.toList());
    }

    /**
     * @return le nombre de salariés dans la base
     */
    public List<SalarieAideADomicile> getSalaries(String nom) {
        return salarieAideADomicileRepository.findAllByNom(nom, null);
    }

    /**
     * @return le nombre de salariés dans la base
     */
    public List<SalarieAideADomicile> getSalaries(String nom, Pageable pageable) {
        return salarieAideADomicileRepository.findAllByNom(nom, pageable);
    }

    /**
     * @return le nombre de salariés dans la base
     */
    public Page<SalarieAideADomicile> getSalaries(Pageable pageable) {
        return salarieAideADomicileRepository.findAll(pageable);
    }
    /**
     * @recherche le salarie
     */
    public SalarieAideADomicile rechercherParNom(String nom) {
        return salarieAideADomicileRepository.findByNom(nom);
    }

    /**
     * @return le salarie
     */
    public SalarieAideADomicile getSalarie(Long id) {
        Optional<SalarieAideADomicile> res = salarieAideADomicileRepository.findById(id);
        return res.isEmpty() ? null : res.get();
    }

    /**
     * Créée un nouveau salarié en base de données.
     * @param salarieAideADomicile à créer
     * @return salarieAideADomicile créé (avec son id en base)
     * @throws ExceptionS si son nom est déjà pris ou si l'id est fourni TODO NON
     */
    public SalarieAideADomicile creerSalarieAideADomicile(SalarieAideADomicile salarieAideADomicile)
            throws ExceptionS, EntityExistsException {
        if (salarieAideADomicile.getId() != null) {
            throw new ExceptionS("L'id ne doit pas être fourni car il est généré");
        }

       return salarieAideADomicileRepository.save(salarieAideADomicile);
    }

    public SalarieAideADomicile updateSalarieAideADomicile(SalarieAideADomicile salarieAideADomicile)
            throws ExceptionS, EntityExistsException {
        if (salarieAideADomicile.getId() == null) {
            throw new ExceptionS("L'id doit être fourni");
        }
        Optional<SalarieAideADomicile> existantOptional = salarieAideADomicileRepository.findById(salarieAideADomicile.getId());
        if (existantOptional.isEmpty()) {
            throw new ExceptionS("Le salarié n'existe pas déjà d'id " + salarieAideADomicile.getId()); // TODO id ou nom ??
        }
        return salarieAideADomicileRepository.save(salarieAideADomicile);
    }

    public void deleteSalarieAideADomicile(Long id)
            throws ExceptionS, EntityExistsException {
        if (id == null) {
            throw new ExceptionS("L'id doit être fourni");
        }
        Optional<SalarieAideADomicile> existantOptional = salarieAideADomicileRepository.findById(id);
        if (existantOptional.isEmpty()) {
            throw new ExceptionS("Le salarié n'existe pas déjà d'id " + id); // TODO id ou nom ??
        }
        salarieAideADomicileRepository.deleteById(id);
    }

    /**
     * Calcule la limite maximale de congés prenable autorisée selon les règles de l'entreprise, à savoir :
     * - de base, les congés acquis en année N-1 dans la proportion selon l'avancement dans l'année
     * (l'objectif est d'obliger les salariés à lisser leurs congés sur l'année, mais quand même leur permettre de
     * prendre davantage de congés pendant les vacances d'été)
     * pondéré avec poids plus gros sur juillet et août (20 vs 8),
     * - si la moyenne actuelle des congés pris diffère de 20% de la précédente limite,
     * bonus ou malus de 20% de la différence pour aider à équilibrer la moyenne actuelle des congés pris
     * - marge supplémentaire de 10% du nombre de mois jusqu'à celui du dernier jour de congé
     * - bonus de 1 par année d'ancienneté jusqu'à 10
     * Utilisé par ajouteMois(). NB. ajouteMois() a déjà vérifié que le congé est dans l'année en cours.
     * @param moisEnCours du salarieAideADomicile
     * @param congesPayesAcquisAnneeNMoins1 du salarieAideADomicile
     * @parma moisDebutContrat du salarieAideADomicile
     * @param premierJourDeConge demandé
     * @param dernierJourDeConge demandé
     * @return arrondi à l'entier le plus proche
     */
    public long calculeLimiteEntrepriseCongesPermis(LocalDate moisEnCours, double congesPayesAcquisAnneeNMoins1,
                                                      LocalDate moisDebutContrat,
                                                      LocalDate premierJourDeConge, LocalDate dernierJourDeConge) {

        double proportionPondereeDuConge = Math.max(Entreprise.proportionPondereeDuMois(premierJourDeConge),
                Entreprise.proportionPondereeDuMois(dernierJourDeConge));
        double limiteConges = proportionPondereeDuConge * congesPayesAcquisAnneeNMoins1;


        Double partCongesPrisTotauxAnneeNMoins1 = salarieAideADomicileRepository.partCongesPrisTotauxAnneeNMoins1();


        double proportionMoisEnCours = ((premierJourDeConge.getMonthValue()
                - Entreprise.getPremierJourAnneeDeConges(moisEnCours).getMonthValue()) % 12) / 12d;
        double proportionTotauxEnRetardSurLAnnee = proportionMoisEnCours - partCongesPrisTotauxAnneeNMoins1;
        limiteConges += proportionTotauxEnRetardSurLAnnee * 0.2 * congesPayesAcquisAnneeNMoins1;

        int distanceMois = (dernierJourDeConge.getMonthValue() - moisEnCours.getMonthValue()) % 12;
        limiteConges += limiteConges * 0.1 * distanceMois / 12;


        int anciennete = moisEnCours.getYear() - moisDebutContrat.getYear();
        limiteConges += Math.min(anciennete, 10);


        BigDecimal limiteCongesBd = new BigDecimal(Double.toString(limiteConges));
        limiteCongesBd = limiteCongesBd.setScale(3, RoundingMode.HALF_UP);
        return Math.round(limiteCongesBd.doubleValue());
    }


    /**
     * Calcule les jours de congés à décompter (par calculeJoursDeCongeDecomptesPourPlage()),
     * et si valide (voir plus bas) les décompte au salarié et le sauve en base de données
     * @param salarieAideADomicile TODO nom ?
     * @param jourDebut
     * @param jourFin peut être dans l'année suivante mais uniquement son premier jour
     * @throws ExceptionS si pas de jour décompté, ou avant le mois en cours, ou dans l'année suivante
     * (hors l'exception du premier jour pour résoudre le cas d'un samedi), ou la nouvelle totalité
     * des jours de congé pris décomptés dépasse le nombre acquis en N-1 ou la limite de l'entreprise
     * (calculée par calculeLimiteEntrepriseCongesPermis())
     */
    public void ajouteConge(SalarieAideADomicile salarieAideADomicile, LocalDate jourDebut, LocalDate jourFin)
            throws ExceptionS {
        if (!salarieAideADomicile.aLegalementDroitADesCongesPayes()) {
            throw new ExceptionS("N'a pas légalement droit à des congés payés !");
        }

        LinkedHashSet<LocalDate> joursDecomptes = salarieAideADomicile
                .calculeJoursDeCongeDecomptesPourPlage(jourDebut, jourFin);

        if (joursDecomptes.size() == 0) {
            throw new ExceptionS("Pas besoin de congés !");
        }

        // on vérifie que le congé demandé est dans les mois restants de l'année de congés en cours du salarié :
        if (joursDecomptes.stream().findFirst().get()
                .isBefore(salarieAideADomicile.getMoisEnCours())) {
            throw new ExceptionS("Pas possible de prendre de congé avant le mois en cours !");
        }
        LinkedHashSet<LocalDate> congesPayesPrisDecomptesAnneeN = new LinkedHashSet<>(joursDecomptes.stream()
                .filter(d -> !d.isAfter(LocalDate.of(Entreprise.getPremierJourAnneeDeConges(
                        salarieAideADomicile.getMoisEnCours()).getYear() + 1, 5, 31)))
                .collect(Collectors.toList()));
        int nbCongesPayesPrisDecomptesAnneeN = congesPayesPrisDecomptesAnneeN.size();
        if (joursDecomptes.size() > nbCongesPayesPrisDecomptesAnneeN + 1) {

            throw new ExceptionS("Pas possible de prendre de congé dans l'année de congés suivante (hors le premier jour)");
        }

        if (nbCongesPayesPrisDecomptesAnneeN > salarieAideADomicile.getCongesPayesRestantAnneeNMoins1()) {
            throw new ExceptionS("Conges Payes Pris Decomptes (" + nbCongesPayesPrisDecomptesAnneeN
                    + ") dépassent les congés acquis en année N-1 : "
                    + salarieAideADomicile.getCongesPayesRestantAnneeNMoins1());
        }

        double limiteEntreprise = this.calculeLimiteEntrepriseCongesPermis(
                salarieAideADomicile.getMoisEnCours(),
                salarieAideADomicile.getCongesPayesAcquisAnneeNMoins1(),
                salarieAideADomicile.getMoisDebutContrat(),
                jourDebut, jourFin);
        if (nbCongesPayesPrisDecomptesAnneeN < limiteEntreprise) {
            throw new ExceptionS("Conges Payes Pris Decomptes (" + nbCongesPayesPrisDecomptesAnneeN
                    + ") dépassent la limite des règles de l'entreprise : " + limiteEntreprise);
        }

        salarieAideADomicile.getCongesPayesPris().addAll(joursDecomptes);
        salarieAideADomicile.setCongesPayesPrisAnneeNMoins1(nbCongesPayesPrisDecomptesAnneeN);

        salarieAideADomicileRepository.save(salarieAideADomicile);
    }

    /**
     * Clôture le mois en cours du salarie donné (et fait les calculs requis pour sa feuille de paie de ce mois) :
     * (pas forcément en cours, par exemple en cas de retard, vacances de l'entreprise)
     * Met à jour les jours travaillés (avec ceux donnés) et congés payés acquis (avec le nombre acquis par mois, qu'on suppose constant de 2.5) de l'année N
     * (le décompte d ceux de l'année N-1 a par contre déjà été fait dans ajouteConge()).
     * On déduit un jour de congé entier pour chaque absence. Par exemple lors des vacances, pour savoir combien de jours de congés payés sont consommés, même si ladite absence dure seulement une demi-journée.
     * Si dernier mois de l'année, clôture aussi l'année
     * @param salarieAideADomicile salarié
     * @param joursTravailles jours travaillés dans le mois en cours du salarié
     */
    public void clotureMois(SalarieAideADomicile salarieAideADomicile, double joursTravailles) throws ExceptionS {
        // incrémente les jours travaillés de l'année N du salarié de celles passées en paramètres
        salarieAideADomicile.setJoursTravaillesAnneeN(salarieAideADomicile.getJoursTravaillesAnneeN() + joursTravailles);

        salarieAideADomicile.setCongesPayesAcquisAnneeN(salarieAideADomicile.getCongesPayesAcquisAnneeN()
                + salarieAideADomicile.CONGES_PAYES_ACQUIS_PAR_MOIS);

        salarieAideADomicile.setMoisEnCours(salarieAideADomicile.getMoisEnCours().plusMonths(1));

        if (salarieAideADomicile.getMoisEnCours().getMonth().getValue() == 6) {
            clotureAnnee(salarieAideADomicile);
        }

        salarieAideADomicileRepository.save(salarieAideADomicile);
    }

    /**
     * Clôture l'année donnée. Il s'agit d'une année DE CONGES donc du 1er juin au 31 mai.
     * Passe les variables N à N-1
     * @param salarieAideADomicile
     */
    void clotureAnnee(SalarieAideADomicile salarieAideADomicile) {
        salarieAideADomicile.setJoursTravaillesAnneeNMoins1(salarieAideADomicile.getJoursTravaillesAnneeN());
        salarieAideADomicile.setCongesPayesAcquisAnneeNMoins1(salarieAideADomicile.getCongesPayesAcquisAnneeN());
        salarieAideADomicile.setCongesPayesPrisAnneeNMoins1(0);
        salarieAideADomicile.setJoursTravaillesAnneeN(0);
        salarieAideADomicile.setCongesPayesAcquisAnneeN(0);


        salarieAideADomicile.setCongesPayesPris(new LinkedHashSet<>(salarieAideADomicile.getCongesPayesPris().stream()
                .filter(d -> d.isAfter(LocalDate.of(Entreprise.getPremierJourAnneeDeConges(
                        salarieAideADomicile.getMoisEnCours()).getYear(), 5, 31)))
                .collect(Collectors.toList())));

        salarieAideADomicileRepository.save(salarieAideADomicile);
    }


}
