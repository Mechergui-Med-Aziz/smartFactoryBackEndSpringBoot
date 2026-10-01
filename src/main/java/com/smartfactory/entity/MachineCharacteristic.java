package com.smartfactory.entity;

public class MachineCharacteristic {

    private String nom;
    private String valeur;
    private String type;

    public MachineCharacteristic() {
    }

    public MachineCharacteristic(String nom, String valeur, String type) {
        this.nom = nom;
        this.valeur = valeur;
        this.type = type;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getValeur() {
        return valeur;
    }

    public void setValeur(String valeur) {
        this.valeur = valeur;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
