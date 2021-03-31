package com.example.coininverst;

import android.widget.ImageView;

public class CCoinItem
{
    public ImageView Logo;
    public String    Name;
    public String    Symbol;
    public String    Rank;
    public String    Value;

    CCoinItem (ImageView logo, String name, String symbol, String rank, String value)
    {
        Logo   = logo;
        Name   = name;
        Symbol = symbol;
        Rank   = rank;
        Value  = value;
    }
}
